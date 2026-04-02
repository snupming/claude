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
public class GoogleLensUploadStrategy implements GoogleReverseImageSearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(GoogleLensUploadStrategy.class);
    private static final String LENS_UPLOAD_URL = "https://lens.google.com/v3/upload";
    private static final Pattern IMGRES_IMGURL = Pattern.compile("[?&]imgurl=([^&]+)");
    private static final Pattern IMGRES_IMGREFURL = Pattern.compile("[?&]imgrefurl=([^&]+)");

    private final HttpClient httpClient;
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
        this.httpClient = TrustingHttpClientFactory.create(props.requestTimeoutSeconds());
    }

    @Override
    public String name() {
        return "LENS_UPLOAD";
    }

    @Override
    public List<ReverseSearchResult> search(byte[] imageBytes, int maxResults) throws GoogleSearchException {
        log.info("[LensUpload] 요청 시작 — imageBytes={}KB", imageBytes.length / 1024);
        try {
            // 1단계: Lens에 이미지 업로드 → 리다이렉트 URL 획득
            String contentType = detectContentType(imageBytes);
            var multipart = MultipartBodyBuilder.buildForLens(imageBytes, "image.jpg", contentType);
            String userAgent = uaRotator.next();

            HttpRequest uploadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(LENS_UPLOAD_URL))
                    .header("User-Agent", userAgent)
                    .header("Content-Type", multipart.contentType())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .header("Referer", "https://lens.google.com/")
                    .header("Origin", "https://lens.google.com")
                    .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                    .POST(multipart.bodyPublisher())
                    .build();

            HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            String lensHtml = uploadResponse.body();
            String lensUrl = uploadResponse.uri().toString();
            int lensStatus = uploadResponse.statusCode();

            log.info("[LensUpload] 1단계 응답 — status={}, redirectUrl={}", lensStatus, lensUrl);

            if (captchaDetector.isCaptchaResponse(lensHtml, lensUrl, lensStatus)) {
                throw new GoogleSearchException("CAPTCHA detected on Lens upload", true, lensStatus);
            }

            // 1단계 결과에서 먼저 InlineDataExtractor로 시도
            List<ReverseSearchResult> results = dataExtractor.extract(lensHtml, maxResults);
            if (!results.isEmpty()) {
                log.info("[LensUpload] 1단계에서 {} 결과 추출", results.size());
                return results;
            }

            // 2단계: vsrid를 유지하고 udm=26 → udm=2로 변경하여 이미지 검색 결과 요청
            if (lensUrl.contains("google.com/search") && lensUrl.contains("vsrid=")) {
                String imageSearchUrl = lensUrl
                        .replaceAll("[&?]udm=\\d+", "")       // udm=26 제거
                        .replaceAll("[&?]lns_mode=[^&]*", "") // lns_mode 제거
                        .replaceAll("[&?]lns_surface=[^&]*", "") // lns_surface 제거
                        .replaceAll("[&?]source=[^&]*", "")   // source 제거
                        + "&udm=2";                            // 이미지 검색 모드
                // tbm=isch도 추가 (호환성)
                if (!imageSearchUrl.contains("tbm=")) {
                    imageSearchUrl += "&tbm=isch";
                }

                log.info("[LensUpload] 2단계: 이미지 탭 요청 — {}", imageSearchUrl);

                HttpRequest imageRequest = HttpRequest.newBuilder()
                        .uri(URI.create(imageSearchUrl))
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                        .header("Referer", lensUrl)
                        .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                        .GET()
                        .build();

                HttpResponse<String> imageResponse = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofString());
                String imageHtml = imageResponse.body();
                String imageFinalUrl = imageResponse.uri().toString();
                int imageStatus = imageResponse.statusCode();

                log.info("[LensUpload] 2단계 응답 — status={}, url={}, bodyLength={}",
                        imageStatus, imageFinalUrl, imageHtml.length());

                if (imageStatus == 200) {
                    results = parseImageResults(imageHtml, maxResults);
                    log.info("[LensUpload] 2단계 파싱 — {} 결과", results.size());
                    if (!results.isEmpty()) {
                        return results;
                    }
                }
            }

            // 3단계: 1단계 HTML에서도 웹 검색 결과 파싱 시도
            results = parseImageResults(lensHtml, maxResults);
            if (!results.isEmpty()) {
                log.info("[LensUpload] 3단계 (1단계 HTML 재파싱) — {} 결과", results.size());
                return results;
            }

            log.warn("[LensUpload] 모든 단계 실패 — 0건, title: {}",
                    Jsoup.parse(lensHtml).title());
            return List.of();

        } catch (GoogleSearchException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LensUpload] 요청 실패", e);
            throw new GoogleSearchException("Lens upload request failed", e);
        }
    }

    /**
     * 구글 이미지 검색 결과 HTML을 파싱한다.
     * imgres 링크, data-ri div, isv-r div, 외부 이미지 썸네일 등 다양한 전략 사용.
     */
    private List<ReverseSearchResult> parseImageResults(String html, int maxResults) {
        Document doc = Jsoup.parse(html);
        List<ReverseSearchResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. /imgres? 링크
        for (Element link : doc.select("a[href*=/imgres?]")) {
            if (results.size() >= maxResults) break;
            String href = link.attr("href");
            String imageUrl = extractParam(href, IMGRES_IMGURL);
            String pageUrl = extractParam(href, IMGRES_IMGREFURL);
            if (imageUrl != null && seen.add(imageUrl)) {
                results.add(new ReverseSearchResult(imageUrl, pageUrl, link.text()));
            }
        }

        // 2. data-ri / isv-r 이미지 그리드
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

        // 3. 외부 이미지 (google/gstatic 제외)
        if (results.isEmpty()) {
            Elements imgs = doc.select("img[src^=http]");
            for (Element img : imgs) {
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
}
