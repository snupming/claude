package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort.ReverseSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
@Profile("google")
public class GoogleLensUploadStrategy implements GoogleReverseImageSearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(GoogleLensUploadStrategy.class);
    private static final String LENS_UPLOAD_URL = "https://lens.google.com/v3/upload";

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
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .build();
    }

    @Override
    public String name() {
        return "LENS_UPLOAD";
    }

    @Override
    public List<ReverseSearchResult> search(byte[] imageBytes, int maxResults) throws GoogleSearchException {
        log.info("[LensUpload] 요청 시작 — imageBytes={}KB", imageBytes.length / 1024);
        try {
            var multipart = MultipartBodyBuilder.build(imageBytes, "image.jpg");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LENS_UPLOAD_URL))
                    .header("User-Agent", uaRotator.next())
                    .header("Content-Type", multipart.contentType())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .header("Referer", "https://lens.google.com/")
                    .header("Origin", "https://lens.google.com")
                    .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                    .POST(multipart.bodyPublisher())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();
            String finalUrl = response.uri().toString();
            int status = response.statusCode();

            log.info("[LensUpload] 응답 — status={}, finalUrl={}, bodyLength={}",
                    status, finalUrl, html.length());

            if (captchaDetector.isCaptchaResponse(html, finalUrl, status)) {
                throw new GoogleSearchException("CAPTCHA detected on Lens upload", true, status);
            }

            if (status != 200) {
                log.warn("[LensUpload] 비정상 상태 — status={}, body(500자)={}",
                        status, html.substring(0, Math.min(html.length(), 500)));
                throw new GoogleSearchException("Lens upload unexpected status: " + status, false, status);
            }

            List<ReverseSearchResult> results = dataExtractor.extract(html, maxResults);
            log.info("[LensUpload] 완료 — {} 결과", results.size());
            return results;

        } catch (GoogleSearchException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LensUpload] 요청 실패", e);
            throw new GoogleSearchException("Lens upload request failed", e);
        }
    }
}
