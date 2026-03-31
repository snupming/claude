package com.ownpic.detection.dto;

import com.ownpic.detection.domain.DetectionScan;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class DetectionScanResponseTest {

    @Test
    void from_calculatesProgressCorrectly() {
        var scan = new DetectionScan(UUID.randomUUID(), 10);
        scan.setScannedImages(7);

        var response = DetectionScanResponse.from(scan);

        assertThat(response.progress()).isEqualTo(70);
        assertThat(response.totalImages()).isEqualTo(10);
        assertThat(response.scannedImages()).isEqualTo(7);
    }

    @Test
    void from_fullProgress_returns100() {
        var scan = new DetectionScan(UUID.randomUUID(), 5);
        scan.setScannedImages(5);

        var response = DetectionScanResponse.from(scan);

        assertThat(response.progress()).isEqualTo(100);
    }

    @Test
    void from_zeroTotalImages_progressIsZero() {
        var scan = new DetectionScan(UUID.randomUUID(), 0);

        var response = DetectionScanResponse.from(scan);

        assertThat(response.progress()).isEqualTo(0);
    }

    @Test
    void from_statusIsScanning() {
        var scan = new DetectionScan(UUID.randomUUID(), 3);

        var response = DetectionScanResponse.from(scan);

        assertThat(response.status()).isEqualTo("SCANNING");
    }

    @Test
    void from_completedScan_hasCompletedAt() {
        var scan = new DetectionScan(UUID.randomUUID(), 3);
        scan.setScannedImages(3);
        scan.setStatus("COMPLETED");
        scan.setCompletedAt(java.time.Instant.now());

        var response = DetectionScanResponse.from(scan);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.completedAt()).isNotNull();
    }
}
