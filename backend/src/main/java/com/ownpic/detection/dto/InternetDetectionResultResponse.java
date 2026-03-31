package com.ownpic.detection.dto;

import com.ownpic.detection.domain.InternetDetectionResult;

import java.time.Instant;

public record InternetDetectionResultResponse(
        Long id,
        Long sourceImageId,
        String foundImageUrl,
        String sourcePageUrl,
        String sourcePageTitle,
        Double sscdSimilarity,
        Double dinoSimilarity,
        String judgment,
        String searchEngine,
        Instant createdAt
) {
    public static InternetDetectionResultResponse from(InternetDetectionResult r) {
        return new InternetDetectionResultResponse(
                r.getId(), r.getSourceImageId(),
                r.getFoundImageUrl(), r.getSourcePageUrl(), r.getSourcePageTitle(),
                r.getSscdSimilarity(), r.getDinoSimilarity(),
                r.getJudgment(), r.getSearchEngine(), r.getCreatedAt());
    }
}
