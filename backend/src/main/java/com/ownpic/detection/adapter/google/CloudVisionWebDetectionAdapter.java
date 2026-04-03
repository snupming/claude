package com.ownpic.detection.adapter.google;

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
 * 수집 순서: pagesWithMatchingImages(URL+제목 있음) → fullMatch → partialMatch → similar
 * 추가 활용: bestGuessLabels(이미지 주제), webEntities(브랜드/카테고리)
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
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(NoCredentialsProvider.create())
                    .setHeaderProvider((HeaderProvider) () -> Map.of("x-goog-api-key", apiKey))
                    .build();
            return ImageAnnotatorClient.create(settings);
        }
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

            // ===== API 응답 전체 로그 =====
            log.info("[CloudVision] ===== API 응답 RAW 시작 =====");

            // bestGuessLabels 전체
            log.info("[CloudVision] bestGuessLabels: {}건", web.getBestGuessLabelsCount());
            for (var label : web.getBestGuessLabelsList()) {
                log.info("[CloudVision]   label='{}' lang='{}'", label.getLabel(), label.getLanguageCode());
            }

            // webEntities 전체
            log.info("[CloudVision] webEntities: {}건", web.getWebEntitiesCount());
            for (var entity : web.getWebEntitiesList()) {
                log.info("[CloudVision]   entity='{}' score={} id='{}'",
                        entity.getDescription(), entity.getScore(), entity.getEntityId());
            }

            // pagesWithMatchingImages 전체
            log.info("[CloudVision] pagesWithMatchingImages: {}건", web.getPagesWithMatchingImagesCount());
            for (var page : web.getPagesWithMatchingImagesList()) {
                log.info("[CloudVision]   page url='{}' title='{}' fullMatch={}건 partialMatch={}건",
                        page.getUrl(), page.getPageTitle(),
                        page.getFullMatchingImagesCount(), page.getPartialMatchingImagesCount());
                for (var img : page.getFullMatchingImagesList()) {
                    log.info("[CloudVision]     fullMatch: {}", img.getUrl());
                }
                for (var img : page.getPartialMatchingImagesList()) {
                    log.info("[CloudVision]     partialMatch: {}", img.getUrl());
                }
            }

            // fullMatchingImages 전체
            log.info("[CloudVision] fullMatchingImages: {}건", web.getFullMatchingImagesCount());
            for (var img : web.getFullMatchingImagesList()) {
                log.info("[CloudVision]   fullMatch: {}", img.getUrl());
            }

            // partialMatchingImages 전체
            log.info("[CloudVision] partialMatchingImages: {}건", web.getPartialMatchingImagesCount());
            for (var img : web.getPartialMatchingImagesList()) {
                log.info("[CloudVision]   partialMatch: {}", img.getUrl());
            }

            // visuallySimilarImages 전체
            log.info("[CloudVision] visuallySimilarImages: {}건", web.getVisuallySimilarImagesCount());
            for (var img : web.getVisuallySimilarImagesList()) {
                log.info("[CloudVision]   similar: {}", img.getUrl());
            }

            log.info("[CloudVision] ===== API 응답 RAW 끝 =====");

            // bestGuessLabels 추출 (이미지 주제 추측)
            String bestGuessLabel = null;
            if (!web.getBestGuessLabelsList().isEmpty()) {
                bestGuessLabel = web.getBestGuessLabels(0).getLabel();
                log.info("[CloudVision] 추정 라벨: {}", bestGuessLabel);
            }

            // webEntities 추출 (최상위 엔터티)
            String topEntity = null;
            for (WebDetection.WebEntity entity : web.getWebEntitiesList()) {
                if (entity.getDescription() != null && !entity.getDescription().isBlank()) {
                    topEntity = entity.getDescription();
                    log.info("[CloudVision] 최상위 엔터티: {} (score={})", topEntity, entity.getScore());
                    break;
                }
            }

            List<ReverseSearchResult> results = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            // ★ 1순위: pagesWithMatchingImages — fullMatch만 (partialMatch는 "함께 보면 좋은 상품" 등 추천 이미지일 가능성 높아 제외)
            for (WebDetection.WebPage page : web.getPagesWithMatchingImagesList()) {
                if (results.size() >= maxResults) break;
                String pageUrl = page.getUrl();
                String title = decodeHtmlEntities(page.getPageTitle());
                for (WebDetection.WebImage img : page.getFullMatchingImagesList()) {
                    if (results.size() >= maxResults) break;
                    if (seen.add(img.getUrl())) {
                        results.add(new ReverseSearchResult(img.getUrl(), pageUrl, title, bestGuessLabel, topEntity));
                    }
                }
                // partialMatch는 제외 — 추천 상품/관련 상품 섹션의 유사 이미지일 가능성 높음
            }
            log.info("[CloudVision] 매칭 페이지: {}건 (URL+제목 포함)", web.getPagesWithMatchingImagesCount());

            // 2순위: fullMatchingImages (이미지 URL만, 페이지 URL 없음)
            for (WebDetection.WebImage match : web.getFullMatchingImagesList()) {
                if (results.size() >= maxResults) break;
                if (match.getUrl() != null && seen.add(match.getUrl())) {
                    results.add(new ReverseSearchResult(match.getUrl(), null, null, bestGuessLabel, topEntity));
                }
            }
            log.info("[CloudVision] 완전 일치: {}건", web.getFullMatchingImagesCount());

            // 3순위: partialMatchingImages
            for (WebDetection.WebImage match : web.getPartialMatchingImagesList()) {
                if (results.size() >= maxResults) break;
                if (match.getUrl() != null && seen.add(match.getUrl())) {
                    results.add(new ReverseSearchResult(match.getUrl(), null, null, bestGuessLabel, topEntity));
                }
            }
            log.info("[CloudVision] 부분 일치: {}건", web.getPartialMatchingImagesCount());

            // 4순위: visuallySimilarImages
            for (WebDetection.WebImage similar : web.getVisuallySimilarImagesList()) {
                if (results.size() >= maxResults) break;
                if (similar.getUrl() != null && seen.add(similar.getUrl())) {
                    results.add(new ReverseSearchResult(similar.getUrl(), null, null, bestGuessLabel, topEntity));
                }
            }
            log.info("[CloudVision] 유사 이미지: {}건", web.getVisuallySimilarImagesCount());

            log.info("[CloudVision] 완료 — 총 {} 결과 (sourcePageUrl 있는 결과: {}건)",
                    results.size(), results.stream().filter(r -> r.sourcePageUrl() != null).count());
            return results;

        } catch (Exception e) {
            log.error("[CloudVision] WebDetection 실패", e);
            return List.of();
        }
    }

    /** pageTitle에 포함된 HTML 엔터티 디코딩 */
    private String decodeHtmlEntities(String text) {
        if (text == null) return null;
        return text.replace("&#39;", "'")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"");
    }
}
