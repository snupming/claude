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
public class GoogleLensUploadStrategy implements GoogleReverseImageSearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(GoogleLensUploadStrategy.class);
    private static final String LENS_UPLOAD_URL = "https://lens.google.com/v3/upload?ep=gsbubb&st=%d&authuser=0&hl=ko&vpw=1707&vph=906";
    private static final Pattern IMGRES_IMGURL = Pattern.compile("[?&]imgurl=([^&]+)");
    private static final Pattern IMGRES_IMGREFURL = Pattern.compile("[?&]imgrefurl=([^&]+)");

    private final HttpClient uploadClient;  // 리다이렉트 안 따라감
    private final HttpClient browseClient;  // 리다이렉트 따라감
    private final UserAgentRotator uaRotator;
    private final CaptchaDetector captchaDetector;
    private final InlineDataExtractor dataExtractor;
    private final GoogleScraperProperties props;

    public GoogleLensUploadStrategy(UserAgentRotator uaRotator,
                                    CaptchaDetector captchaDetector,
                                    InlineDataExtractor dataExtractor,
                                    GoogleScraperProperties props) {
        this.uaRotator = uaRotator;
        this.captchaDetector = captchaDetector;
        this.dataExtractor = dataExtractor;
        this.props = props;
        this.uploadClient = TrustingHttpClientFactory.create(props.requestTimeoutSeconds(), false);
        this.browseClient = TrustingHttpClientFactory.create(props.requestTimeoutSeconds(), true);
    }

    @Override
    public String name() {
        return "LENS_UPLOAD";
    }

    @Override
    public List<ReverseSearchResult> search(byte[] imageBytes, int maxResults) throws GoogleSearchException {
        log.info("[LensUpload] 요청 시작 — imageBytes={}KB", imageBytes.length / 1024);
        try {
            String contentType = detectContentType(imageBytes);
            var multipart = MultipartBodyBuilder.buildForLens(imageBytes, "image.jpg", contentType);
            String userAgent = uaRotator.next();
            String uploadUrl = String.format(LENS_UPLOAD_URL, System.currentTimeMillis());

            // 1단계: 이미지 업로드 (리다이렉트 안 따라감 → Location 헤더에서 vsrid URL 캡처)
            HttpRequest uploadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("User-Agent", userAgent)
                    .header("Content-Type", multipart.contentType())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .header("Referer", "https://www.google.com/")
                    .header("Origin", "https://www.google.com")
                    .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                    .POST(multipart.bodyPublisher())
                    .build();

            HttpResponse<String> uploadResponse = uploadClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            int uploadStatus = uploadResponse.statusCode();
            String redirectUrl = uploadResponse.headers().firstValue("Location").orElse(null);

            log.info("[LensUpload] 1단계 — status={}, Location={}", uploadStatus, redirectUrl);

            // 리다이렉트가 아니면 (200이면) body에서 직접 파싱
            if (uploadStatus == 200) {
                String html = uploadResponse.body();
                List<ReverseSearchResult> results = dataExtractor.extract(html, maxResults);
                if (!results.isEmpty()) {
                    log.info("[LensUpload] 1단계 직접 파싱 — {} 결과", results.size());
                    return results;
                }
                results = parseImageResults(html, maxResults);
                if (!results.isEmpty()) return results;
            }

            // 리다이렉트 URL 없으면 실패
            if (redirectUrl == null || !redirectUrl.contains("vsrid=")) {
                // 3xx 상태인데 Location이 없거나, vsrid가 없으면
                // 자동 리다이렉트로 다시 시도
                log.warn("[LensUpload] vsrid 미포함 — status={}, 자동 리다이렉트로 재시도", uploadStatus);
                HttpResponse<String> autoResponse = browseClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
                String autoUrl = autoResponse.uri().toString();
                log.info("[LensUpload] 자동 리다이렉트 — finalUrl={}", autoUrl);

                List<ReverseSearchResult> results = dataExtractor.extract(autoResponse.body(), maxResults);
                if (!results.isEmpty()) return results;
                return parseImageResults(autoResponse.body(), maxResults);
            }

            log.info("[LensUpload] vsrid URL 캡처 — {}", redirectUrl);

            // 2단계: vsrid URL로 직접 GET 요청 (브라우저처럼)
            HttpRequest searchRequest = HttpRequest.newBuilder()
                    .uri(URI.create(redirectUrl))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .header("Referer", "https://www.google.com/")
                    .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<String> searchResponse = browseClient.send(searchRequest, HttpResponse.BodyHandlers.ofString());
            String searchHtml = searchResponse.body();
            String searchFinalUrl = searchResponse.uri().toString();

            log.info("[LensUpload] 2단계 — status={}, url={}, bodyLength={}",
                    searchResponse.statusCode(), searchFinalUrl, searchHtml.length());

            if (captchaDetector.isCaptchaResponse(searchHtml, searchFinalUrl, searchResponse.statusCode())) {
                throw new GoogleSearchException("CAPTCHA detected", true, searchResponse.statusCode());
            }

            // InlineDataExtractor → HTML 파싱 순서로 시도
            List<ReverseSearchResult> results = dataExtractor.extract(searchHtml, maxResults);
            if (!results.isEmpty()) {
                log.info("[LensUpload] 2단계 InlineData — {} 결과", results.size());
                return results;
            }

            results = parseImageResults(searchHtml, maxResults);
            if (!results.isEmpty()) {
                log.info("[LensUpload] 2단계 HTML 파싱 — {} 결과", results.size());
                return results;
            }

            log.warn("[LensUpload] 모든 단계 실패 — 0건, title: {}, AF블록: {}",
                    Jsoup.parse(searchHtml).title(),
                    countOccurrences(searchHtml, "AF_initDataCallback"));
            return List.of();

        } catch (GoogleSearchException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LensUpload] 요청 실패", e);
            throw new GoogleSearchException("Lens upload request failed", e);
        }
    }

    private List<ReverseSearchResult> parseImageResults(String html, int maxResults) {
        Document doc = Jsoup.parse(html);
        List<ReverseSearchResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Element link : doc.select("a[href*=/imgres?]")) {
            if (results.size() >= maxResults) break;
            String href = link.attr("href");
            String imageUrl = extractParam(href, IMGRES_IMGURL);
            String pageUrl = extractParam(href, IMGRES_IMGREFURL);
            if (imageUrl != null && seen.add(imageUrl)) {
                results.add(new ReverseSearchResult(imageUrl, pageUrl, link.text()));
            }
        }

        if (results.isEmpty()) {
            for (Element div : doc.select("div[data-ri], div.isv-r")) {
                if (results.size() >= maxResults) break;
                Element img = div.selectFirst("img[src], img[data-src]");
                Element anchor = div.selectFirst("a[href]");
                if (img == null) continue;
                String imgSrc = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
                if (imgSrc.isEmpty() || imgSrc.startsWith("data:")) continue;
                String pageUrl = anchor != null ? anchor.attr("href") : null;
                if (pageUrl != null && pageUrl.startsWith("/")) pageUrl = null;
                if (seen.add(imgSrc)) {
                    results.add(new ReverseSearchResult(imgSrc, pageUrl, img.attr("alt")));
                }
            }
        }

        if (results.isEmpty()) {
            for (Element img : doc.select("img[src^=http]")) {
                if (results.size() >= maxResults) break;
                String src = img.attr("src");
                if (src.contains("google.") || src.contains("gstatic.") || src.contains("youtube.")) continue;
                Element parentLink = img.closest("a[href^=http]");
                String pageUrl = parentLink != null ? parentLink.attr("href") : null;
                if (pageUrl != null && pageUrl.contains("google.")) pageUrl = null;
                if (seen.add(src)) {
                    results.add(new ReverseSearchResult(src, pageUrl, img.attr("alt")));
                }
            }
        }

        return results;
    }

    private String extractParam(String url, Pattern pattern) {
        Matcher m = pattern.matcher(url);
        if (!m.find()) return null;
        return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return "image/jpeg";
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) return "image/png";
        if (bytes.length >= 4 && bytes[0] == 0x52 && bytes[1] == 0x49) return "image/webp";
        return "image/jpeg";
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
        return count;
    }
}
