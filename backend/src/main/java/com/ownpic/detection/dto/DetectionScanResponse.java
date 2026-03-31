package com.ownpic.detection.dto;

import com.ownpic.detection.domain.DetectionScan;

import java.time.Instant;

public record DetectionScanResponse(
        Long id,
        String status,
        String scanType,
        int totalImages,
        int scannedImages,
        int matchesFound,
        int progress,
        Instant createdAt,
        Instant completedAt
) {
    public static DetectionScanResponse from(DetectionScan scan) {
        int progress = scan.getTotalImages() > 0
                ? Math.round((float) scan.getScannedImages() / scan.getTotalImages() * 100)
                : 0;
        return new DetectionScanResponse(
                scan.getId(), scan.getStatus(), scan.getScanType(),
                scan.getTotalImages(), scan.getScannedImages(),
                scan.getMatchesFound(), progress,
                scan.getCreatedAt(), scan.getCompletedAt());
    }
}
