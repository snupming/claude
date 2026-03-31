package com.ownpic.detection.dto;

import com.ownpic.detection.domain.DetectionResult;

import java.time.Instant;
import java.util.UUID;

public record DetectionResultResponse(
        Long id,
        Long sourceImageId,
        Long matchedImageId,
        UUID matchedUserId,
        Double sscdSimilarity,
        Double dinoSimilarity,
        String judgment,
        Instant createdAt
) {
    public static DetectionResultResponse from(DetectionResult result) {
        return new DetectionResultResponse(
                result.getId(),
                result.getSourceImageId(),
                result.getMatchedImageId(),
                result.getMatchedUserId(),
                result.getSscdSimilarity(),
                result.getDinoSimilarity(),
                result.getJudgment(),
                result.getCreatedAt());
    }
}
