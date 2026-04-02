package com.ownpic.detection.adapter.google;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.ownpic.detection.port.ReverseImageSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Google Cloud Vision API — WebDetection 기반 리버스 이미지 검색.
 *
 * 활성화: SPRING_PROFILES_ACTIVE=google
 * 인증: GOOGLE_APPLICATION_CREDENTIALS 환경변수에 서비스 계정 JSON 경로 설정
 * 가격: 월 1,000건 무료, 이후 1,000건당 $3.50
 */
@Component
@Profile("google")
public class CloudVisionWebDetectionAdapter implements ReverseImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(CloudVisionWebDetectionAdapter.class);

    public CloudVisionWebDetectionAdapter() {
        log.info("[CloudVision] WebDetection adapter ENABLED");
    }

    @Override
    public List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults) {
        log.info("[CloudVision] WebDetection 시작 — imageBytes={}KB, maxResults={}", imageBytes.length / 1024, maxResults);

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
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
