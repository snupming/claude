package com.ownpic.detection.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "detection_scans")
public class DetectionScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "total_images", nullable = false)
    private int totalImages;

    @Column(name = "scanned_images", nullable = false)
    private int scannedImages;

    @Column(name = "matches_found", nullable = false)
    private int matchesFound;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "scan_type", nullable = false, length = 20)
    private String scanType = "INTERNET";

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DetectionScan() {}

    public DetectionScan(UUID userId, int totalImages) {
        this.userId = userId;
        this.totalImages = totalImages;
        this.status = "SCANNING";
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public int getTotalImages() { return totalImages; }
    public int getScannedImages() { return scannedImages; }
    public int getMatchesFound() { return matchesFound; }
    public Instant getCreatedAt() { return createdAt; }
    public String getScanType() { return scanType; }
    public void setScanType(String scanType) { this.scanType = scanType; }
    public Instant getCompletedAt() { return completedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setScannedImages(int scannedImages) { this.scannedImages = scannedImages; }
    public void setMatchesFound(int matchesFound) { this.matchesFound = matchesFound; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
