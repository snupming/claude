package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort.ReverseSearchResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("google")
public class GoogleSearchByImageStrategy implements GoogleReverseImageSearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(GoogleSearchByImageStrategy.class);
    private static final String UPLOAD_URL = "https://www.google.com/searchbyimage/upload";
    private static final Pattern IMGRES_IMGURL = Pattern.compile("[?&]imgurl=([^&]+)");
    private static final Pattern IMGRES_IMGREFURL = Pattern.compile("[?&]imgrefurl=([^&]+)");

    private final HttpClient httpClient;
    private final UserAgentRotator uaRotator;
    private final CaptchaDetector captchaDetector;
    private final GoogleScraperProperties props;

    public GoogleSearchByImageStrategy(UserAgentRotator uaRotator,
                                       CaptchaDetector captchaDetector,
                                       GoogleScraperProperties props) {
        this.uaRotator = uaRotator;
        this.captchaDetector = captchaDetector;
        this.props = props;
        this.httpClient = TrustingHttpClientFactory.create(props.requestTimeoutSeconds());
    }

    @Override
    public String name() {
        return "SEARCH_BY_IMAGE";
    }

    @Override
    public List<ReverseSearchResult> search(byte[] imageBytes, int maxResults) throws GoogleSearchException {
        log.info("[SearchByImage] 요청 시작 — imageBytes={}KB", imageBytes.length / 1024);
        try {
            var multipart = MultipartBodyBuilder.build(imageBytes, "image.jpg");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(UPLOAD_URL))
                    .header("User-Agent", uaRotator.next())
                    .header("Content-Type", multipart.contentType())
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                    .POST(multipart.bodyPublisher())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();
            String finalUrl = response.uri().toString();
            int status = response.statusCode();

            log.info("[SearchByImage] 응답 — status={}, finalUrl={}, bodyLength={}",
                    status, finalUrl, html.length());

            if (captchaDetector.isCaptchaResponse(html, finalUrl, status)) {
                throw new GoogleSearchException("CAPTCHA detected on searchbyimage", true, status);
            }

            if (status != 200) {
                log.warn("[SearchByImage] 비정상 상태 — status={}, body(500자)={}",
                        status, html.substring(0, Math.min(html.length(), 500)));
                throw new GoogleSearchException("Unexpected status: " + status, false, status);
            }

            return parseSearchResults(html, maxResults);

        } catch (GoogleSearchException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SearchByImage] 요청 실패", e);
            throw new GoogleSearchException("searchbyimage request failed", e);
        }
    }

    private List<ReverseSearchResult> parseSearchResults(String html, int maxResults) {
        Document doc = Jsoup.parse(html);
        List<ReverseSearchResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Strategy 1: /imgres? links (image search grid)
        Elements imgresLinks = doc.select("a[href*=/imgres?]");
        log.info("[SearchByImage] 파싱 — imgres 링크: {}개", imgresLinks.size());
        for (Element link : imgresLinks) {
            if (results.size() >= maxResults) break;
            ReverseSearchResult result = parseImgresLink(link);
            if (result != null && seen.add(result.imageUrl())) {
                results.add(result);
            }
        }

        // Strategy 2: data-ri / isv-r divs (image grid)
        if (results.isEmpty()) {
            Elements gridDivs = doc.select("div[data-ri], div.isv-r");
            log.info("[SearchByImage] 파싱 — grid div: {}개", gridDivs.size());
            for (Element div : gridDivs) {
                if (results.size() >= maxResults) break;
                ReverseSearchResult result = parseResultDiv(div);
                if (result != null && seen.add(result.imageUrl())) {
                    results.add(result);
                }
            }
        }

        // Strategy 3: 웹 검색 결과에서 이미지 추출 (구글이 일반 검색 결과를 반환하는 경우)
        if (results.isEmpty()) {
            // 검색 결과 링크 (외부 사이트 링크)
            Elements searchLinks = doc.select("a[href^=http]");
            log.info("[SearchByImage] 파싱 — 웹 검색 링크: {}개", searchLinks.size());
            for (Element link : searchLinks) {
                if (results.size() >= maxResults) break;
                String href = link.attr("href");
                // 구글 내부 링크 제외
                if (href.contains("google.com") || href.contains("gstatic.com")
                        || href.contains("youtube.com") || href.contains("accounts.google")) continue;
                // data: URL 제외
                if (href.startsWith("data:")) continue;

                String title = link.text();
                if (title.isBlank()) continue;

                // 이미지가 포함된 링크 찾기
                Element img = link.selectFirst("img[src^=http]");
                String imageUrl = img != null ? img.attr("src") : null;

                // 이미지가 없으면 href 자체를 소스 페이지 URL로 사용
                if (imageUrl != null && seen.add(imageUrl)) {
                    results.add(new ReverseSearchResult(imageUrl, href, title));
                } else if (seen.add(href)) {
                    // 이미지 URL이 없는 경우: href가 이미지일 수 있음
                    if (href.matches(".*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$")) {
                        results.add(new ReverseSearchResult(href, null, title));
                    }
                }
            }
        }

        // Strategy 4: "유사한 이미지" 섹션의 썸네일 이미지
        if (results.isEmpty()) {
            Elements thumbnails = doc.select("img[src^=http]:not([src*=google]):not([src*=gstatic])");
            log.info("[SearchByImage] 파싱 — 외부 이미지 썸네일: {}개", thumbnails.size());
            for (Element img : thumbnails) {
                if (results.size() >= maxResults) break;
                String src = img.attr("src");
                if (src.isEmpty() || src.startsWith("data:")) continue;
                Element parentLink = img.closest("a[href^=http]");
                String pageUrl = parentLink != null ? parentLink.attr("href") : null;
                if (pageUrl != null && pageUrl.contains("google.com")) pageUrl = null;
                String title = img.attr("alt");
                if (seen.add(src)) {
                    results.add(new ReverseSearchResult(src, pageUrl, title));
                }
            }
        }

        if (results.isEmpty()) {
            log.warn("[SearchByImage] 파싱 결과 0건 — HTML title: {}, body(500자): {}",
                    doc.title(), html.substring(0, Math.min(html.length(), 500)));
        } else {
            log.info("[SearchByImage] 파싱 완료 — {} 결과", results.size());
        }
        return results;
    }

    private ReverseSearchResult parseImgresLink(Element link) {
        String href = link.attr("href");
        String imageUrl = extractParam(href, IMGRES_IMGURL);
        String sourcePageUrl = extractParam(href, IMGRES_IMGREFURL);
        if (imageUrl == null) return null;

        String title = link.text();
        if (title.isBlank()) {
            Element img = link.selectFirst("img");
            title = img != null ? img.attr("alt") : "";
        }

        return new ReverseSearchResult(imageUrl, sourcePageUrl, title);
    }

    private ReverseSearchResult parseResultDiv(Element div) {
        Element img = div.selectFirst("img[src], img[data-src]");
        Element anchor = div.selectFirst("a[href]");
        if (img == null || anchor == null) return null;

        String imageUrl = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
        if (imageUrl.isEmpty() || imageUrl.startsWith("data:")) return null;

        String sourcePageUrl = anchor.attr("href");
        if (sourcePageUrl.startsWith("/")) return null;

        String title = img.attr("alt");
        return new ReverseSearchResult(imageUrl, sourcePageUrl, title);
    }

    private String extractParam(String url, Pattern pattern) {
        Matcher m = pattern.matcher(url);
        if (!m.find()) return null;
        return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
    }
}
