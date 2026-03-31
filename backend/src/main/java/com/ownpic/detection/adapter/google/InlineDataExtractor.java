package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort.ReverseSearchResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Lens 결과 HTML에서 이미지 URL과 소스 페이지를 추출하는 동적 파서.
 * AF_initDataCallback 블록의 내부 구조(배열 depth)가 변경되어도,
 * URL 정규식 스캔 방식으로 자동 대응한다.
 */
@Component
@Profile("google")
public class InlineDataExtractor {

    private static final Logger log = LoggerFactory.getLogger(InlineDataExtractor.class);

    // AF_initDataCallback 블록 시작 패턴
    private static final String AF_CALLBACK_PREFIX = "AF_initDataCallback(";

    // 이미지 URL 패턴 (jpg, jpeg, png, webp, gif, bmp)
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "\"(https?://[^\"\\s]+\\.(?:jpg|jpeg|png|webp|gif|bmp)(?:\\?[^\"\\s]*)?)\"",
            Pattern.CASE_INSENSITIVE
    );

    // 일반 URL 패턴
    private static final Pattern PAGE_URL_PATTERN = Pattern.compile(
            "\"(https?://[^\"\\s]{10,})\"");

    // 구글 인프라 도메인 (필터링 대상)
    private static final Set<String> GOOGLE_DOMAINS = Set.of(
            "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
            "google.co.kr", "google.co.jp", "ggpht.com", "youtube.com",
            "googlesyndication.com", "doubleclick.net"
    );

    public List<ReverseSearchResult> extract(String html, int maxResults) {
        // 1차: AF_initDataCallback 블록에서 추출
        List<String> dataBlocks = findAfInitDataBlocks(html);
        if (!dataBlocks.isEmpty()) {
            List<ReverseSearchResult> results = extractFromDataBlocks(dataBlocks, maxResults);
            if (!results.isEmpty()) {
                log.debug("Extracted {} results from AF_initDataCallback blocks", results.size());
                return results;
            }
        }

        // 2차: Jsoup HTML 파싱 fallback
        List<ReverseSearchResult> results = extractFromHtml(html, maxResults);
        log.debug("Extracted {} results from HTML fallback", results.size());
        return results;
    }

    /**
     * HTML에서 모든 AF_initDataCallback 블록의 data 부분을 추출한다.
     * 괄호 카운터를 사용하여 중첩된 배열/객체 구조를 정확히 매칭한다.
     */
    List<String> findAfInitDataBlocks(String html) {
        List<String> blocks = new ArrayList<>();
        int searchFrom = 0;

        while (true) {
            int start = html.indexOf(AF_CALLBACK_PREFIX, searchFrom);
            if (start < 0) break;

            int bodyStart = start + AF_CALLBACK_PREFIX.length();
            int end = findMatchingClose(html, bodyStart, '(', ')');
            if (end < 0) {
                searchFrom = bodyStart;
                continue;
            }

            String block = html.substring(bodyStart, end);
            blocks.add(block);
            searchFrom = end + 1;
        }

        return blocks;
    }

    /**
     * 데이터 블록들에서 URL 정규식 스캔으로 이미지 결과를 추출한다.
     * 구조(배열 depth)에 의존하지 않고, URL 패턴만으로 탐색한다.
     */
    private List<ReverseSearchResult> extractFromDataBlocks(List<String> blocks, int maxResults) {
        // 모든 블록에서 이미지 URL과 페이지 URL을 수집
        List<UrlMatch> imageMatches = new ArrayList<>();
        List<UrlMatch> pageMatches = new ArrayList<>();

        for (String block : blocks) {
            collectImageUrls(block, imageMatches);
            collectPageUrls(block, pageMatches);
        }

        // 이미지 URL마다 근접한 페이지 URL을 매칭
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ReverseSearchResult> results = new ArrayList<>();

        for (UrlMatch imgMatch : imageMatches) {
            if (results.size() >= maxResults) break;
            if (!seen.add(imgMatch.url)) continue;

            String sourcePageUrl = findNearestPageUrl(imgMatch, pageMatches);
            String title = inferTitle(sourcePageUrl);
            results.add(new ReverseSearchResult(imgMatch.url, sourcePageUrl, title));
        }

        return results;
    }

    private void collectImageUrls(String block, List<UrlMatch> out) {
        Matcher m = IMAGE_URL_PATTERN.matcher(block);
        while (m.find()) {
            String url = m.group(1);
            if (isExternalUrl(url)) {
                out.add(new UrlMatch(url, m.start()));
            }
        }
    }

    private void collectPageUrls(String block, List<UrlMatch> out) {
        Matcher m = PAGE_URL_PATTERN.matcher(block);
        while (m.find()) {
            String url = m.group(1);
            if (isExternalUrl(url) && !isImageUrl(url)) {
                out.add(new UrlMatch(url, m.start()));
            }
        }
    }

    /**
     * 이미지 URL에서 500자 이내에 있는 가장 가까운 페이지 URL을 찾는다.
     * Google Lens 데이터 구조에서 이미지 URL과 소스 페이지 URL은 인접 위치에 있다.
     */
    private String findNearestPageUrl(UrlMatch imageMatch, List<UrlMatch> pageMatches) {
        int proximity = 500;
        UrlMatch nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (UrlMatch pm : pageMatches) {
            int dist = Math.abs(pm.position - imageMatch.position);
            if (dist < proximity && dist < minDist) {
                nearest = pm;
                minDist = dist;
            }
        }

        return nearest != null ? nearest.url : null;
    }

    /**
     * HTML에서 직접 이미지 결과를 파싱하는 fallback 메서드.
     * AF_initDataCallback이 없거나 파싱에 실패한 경우 사용.
     */
    private List<ReverseSearchResult> extractFromHtml(String html, int maxResults) {
        Document doc = Jsoup.parse(html);
        List<ReverseSearchResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Lens 결과 카드: 이미지를 포함하는 앵커 태그
        for (Element anchor : doc.select("a[href]")) {
            if (results.size() >= maxResults) break;

            Element img = anchor.selectFirst("img[src]");
            if (img == null) continue;

            String imgSrc = img.attr("src");
            if (imgSrc.startsWith("data:") || imgSrc.isEmpty()) continue;
            if (!isExternalUrl(imgSrc)) continue;

            String href = anchor.attr("href");
            if (href.startsWith("/") || !isExternalUrl(href)) continue;

            if (seen.add(imgSrc)) {
                String title = img.attr("alt");
                results.add(new ReverseSearchResult(imgSrc, href, title));
            }
        }

        return results;
    }

    private boolean isExternalUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            return GOOGLE_DOMAINS.stream().noneMatch(host::endsWith);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isImageUrl(String url) {
        return IMAGE_URL_PATTERN.matcher("\"" + url + "\"").find();
    }

    private String inferTitle(String sourcePageUrl) {
        if (sourcePageUrl == null) return "";
        try {
            return URI.create(sourcePageUrl).getHost();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 주어진 위치에서 시작하여 매칭되는 닫는 괄호/대괄호 위치를 찾는다.
     */
    private int findMatchingClose(String text, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (c == '\\') {
                    i++; // escape 다음 문자 스킵
                } else if (c == stringChar) {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    private record UrlMatch(String url, int position) {}
}
