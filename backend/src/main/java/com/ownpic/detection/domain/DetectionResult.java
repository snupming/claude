package com.ownpic.detection.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "detection_results")
public class DetectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false)
    private Long scanId;

    @Column(name = "source_image_id", nullable = false)
    private Long sourceImageId;

    @Column(name = "matched_image_id", nullable = false)
    private Long matchedImageId;

    @Column(name = "matched_user_id", nullable = false)
    private UUID matchedUserId;

    @Column(name = "sscd_similarity")
    private Double sscdSimilarity;

    @Column(name = "dino_similarity")
    private Double dinoSimilarity;

    @Column(nullable = false, length = 20)
    private String judgment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DetectionResult() {}

    public DetectionResult(Long scanId, Long sourceImageId, Long matchedImageId,
                           UUID matchedUserId, Double sscdSimilarity, Double dinoSimilarity,
                           String judgment) {
        this.scanId = scanId;
        this.sourceImageId = sourceImageId;
        this.matchedImageId = matchedImageId;
        this.matchedUserId = matchedUserId;
        this.sscdSimilarity = sscdSimilarity;
        this.dinoSimilarity = dinoSimilarity;
        this.judgment = judgment;
    }

    public Long getId() { return id; }
    public Long getScanId() { return scanId; }
    public Long getSourceImageId() { return sourceImageId; }
    public Long getMatchedImageId() { return matchedImageId; }
    public UUID getMatchedUserId() { return matchedUserId; }
    public Double getSscdSimilarity() { return sscdSimilarity; }
    public Double getDinoSimilarity() { return dinoSimilarity; }
    public String getJudgment() { return judgment; }
    public Instant getCreatedAt() { return createdAt; }
}
