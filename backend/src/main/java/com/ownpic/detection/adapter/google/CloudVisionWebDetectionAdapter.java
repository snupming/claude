package com.ownpic.detection.adapter.google;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.ownpic.detection.port.ReverseImageSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Google Cloud Vision API — WebDetection 기반 리버스 이미지 검색.
 *
 * 인증 방식 (둘 중 하나):
 * 1. API 키: GOOGLE_VISION_API_KEY 환경변수
 * 2. 서비스 계정: GOOGLE_APPLICATION_CREDENTIALS 환경변수
 *
 * 가격: 월 1,000건 무료, 이후 1,000건당 $3.50
 */
@Component
@Profile("google")
public class CloudVisionWebDetectionAdapter implements ReverseImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(CloudVisionWebDetectionAdapter.class);

    private final String apiKey;

    public CloudVisionWebDetectionAdapter(
            @Value("${ownpic.google.vision-api-key:}") String apiKey) {
        this.apiKey = apiKey;
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("[CloudVision] WebDetection adapter ENABLED (API Key 인증)");
        } else {
            log.info("[CloudVision] WebDetection adapter ENABLED (서비스 계정 인증)");
        }
    }

    private ImageAnnotatorClient createClient() throws Exception {
        if (apiKey != null && !apiKey.isBlank()) {
            // API 키 인증
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(NoCredentialsProvider.create())
                    .setHeaderProvider((HeaderProvider) () -> Map.of("x-goog-api-key", apiKey))
                    .build();
            return ImageAnnotatorClient.create(settings);
        }
        // 서비스 계정 (GOOGLE_APPLICATION_CREDENTIALS) 자동 인증
        return ImageAnnotatorClient.create();
    }

    @Override
    public List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults) {
        log.info("[CloudVision] WebDetection 시작 — imageBytes={}KB, maxResults={}", imageBytes.length / 1024, maxResults);

        try (ImageAnnotatorClient client = createClient()) {
            Image image = Image.newBuilder()
                    .setContent(ByteString.copyFrom(imageBytes))
                    .build();

            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.WEB_DETECTION)
                    .setMaxResults(maxResults)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .build();

            BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(List.of(request));
            AnnotateImageResponse response = batchResponse.getResponses(0);

            if (response.hasError()) {
                log.error("[CloudVision] API 에러: {}", response.getError().getMessage());
                return List.of();
            }

            WebDetection web = response.getWebDetection();
            List<ReverseSearchResult> results = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            // 1. 완전 일치 이미지
            for (WebDetection.WebImage match : web.getFullMatchingImagesList()) {
                if (results.size() >= maxResults) break;
                if (match.getUrl() != null && seen.add(match.getUrl())) {
                    results.add(new ReverseSearchResult(match.getUrl(), null, null));
                }
            }
            log.info("[CloudVision] 완전 일치: {}건", web.getFullMatchingImagesCount());

            // 2. 부분 일치 이미지
            for (WebDetection.WebImage match : web.getPartialMatchingImagesList()) {
                if (results.size() >= maxResults) break;
                if (match.getUrl() != null && seen.add(match.getUrl())) {
                    results.add(new ReverseSearchResult(match.getUrl(), null, null));
                }
            }
            log.info("[CloudVision] 부분 일치: {}건", web.getPartialMatchingImagesCount());

            // 3. 이미지가 포함된 페이지
            for (WebDetection.WebPage page : web.getPagesWithMatchingImagesList()) {
                if (results.size() >= maxResults) break;
                String pageUrl = page.getUrl();
                String title = page.getPageTitle();
                for (WebDetection.WebImage img : page.getFullMatchingImagesList()) {
                    if (results.size() >= maxResults) break;
                    if (seen.add(img.getUrl())) {
                        results.add(new ReverseSearchResult(img.getUrl(), pageUrl, title));
                    }
                }
                for (WebDetection.WebImage img : page.getPartialMatchingImagesList()) {
                    if (results.size() >= maxResults) break;
                    if (seen.add(img.getUrl())) {
                        results.add(new ReverseSearchResult(img.getUrl(), pageUrl, title));
                    }
                }
            }
            log.info("[CloudVision] 매칭 페이지: {}건", web.getPagesWithMatchingImagesCount());

            // 4. 시각적 유사 이미지
            for (WebDetection.WebImage similar : web.getVisuallySimilarImagesList()) {
                if (results.size() >= maxResults) break;
                if (similar.getUrl() != null && seen.add(similar.getUrl())) {
                    results.add(new ReverseSearchResult(similar.getUrl(), null, null));
                }
            }
            log.info("[CloudVision] 유사 이미지: {}건", web.getVisuallySimilarImagesCount());

            log.info("[CloudVision] 완료 — 총 {} 결과", results.size());
            return results;

        } catch (Exception e) {
            log.error("[CloudVision] WebDetection 실패", e);
            return List.of();
        }
    }
}
