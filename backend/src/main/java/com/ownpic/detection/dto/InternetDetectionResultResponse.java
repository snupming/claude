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
        Instant createdAt,
        String platformType,
        String sellerName,
        String businessRegNumber,
        String representativeName,
        String businessAddress,
        String contactPhone,
        String contactEmail,
        String storeUrl,
        String mailOrderNumber,
        String bestGuessLabel,
        String detectedEntity
) {
    public static InternetDetectionResultResponse from(InternetDetectionResult r) {
        return new InternetDetectionResultResponse(
                r.getId(), r.getSourceImageId(),
                r.getFoundImageUrl(), r.getSourcePageUrl(), r.getSourcePageTitle(),
                r.getSscdSimilarity(), r.getDinoSimilarity(),
                r.getJudgment(), r.getSearchEngine(), r.getCreatedAt(),
                r.getPlatformType(), r.getSellerName(), r.getBusinessRegNumber(),
                r.getRepresentativeName(), r.getBusinessAddress(),
                r.getContactPhone(), r.getContactEmail(), r.getStoreUrl(),
                r.getMailOrderNumber(),
                r.getBestGuessLabel(), r.getDetectedEntity());
    }
}
