package com.ownpic.detection.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "internet_detection_results")
public class InternetDetectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false)
    private Long scanId;

    @Column(name = "source_image_id", nullable = false)
    private Long sourceImageId;

    @Column(name = "found_image_url", nullable = false, length = 2000)
    private String foundImageUrl;

    @Column(name = "source_page_url", length = 2000)
    private String sourcePageUrl;

    @Column(name = "source_page_title", length = 500)
    private String sourcePageTitle;

    @Column(name = "sscd_similarity")
    private Double sscdSimilarity;

    @Column(name = "dino_similarity")
    private Double dinoSimilarity;

    @Column(nullable = false, length = 20)
    private String judgment;

    @Column(name = "search_engine", nullable = false, length = 20)
    private String searchEngine = "NAVER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected InternetDetectionResult() {}

    public InternetDetectionResult(Long scanId, Long sourceImageId,
                                   String foundImageUrl, String sourcePageUrl, String sourcePageTitle,
                                   Double sscdSimilarity, Double dinoSimilarity,
                                   String judgment, String searchEngine) {
        this.scanId = scanId;
        this.sourceImageId = sourceImageId;
        this.foundImageUrl = foundImageUrl;
        this.sourcePageUrl = sourcePageUrl;
        this.sourcePageTitle = sourcePageTitle;
        this.sscdSimilarity = sscdSimilarity;
        this.dinoSimilarity = dinoSimilarity;
        this.judgment = judgment;
        this.searchEngine = searchEngine;
    }

    public Long getId() { return id; }
    public Long getScanId() { return scanId; }
    public Long getSourceImageId() { return sourceImageId; }
    public String getFoundImageUrl() { return foundImageUrl; }
    public String getSourcePageUrl() { return sourcePageUrl; }
    public String getSourcePageTitle() { return sourcePageTitle; }
    public Double getSscdSimilarity() { return sscdSimilarity; }
    public Double getDinoSimilarity() { return dinoSimilarity; }
    public String getJudgment() { return judgment; }
    public String getSearchEngine() { return searchEngine; }
    public Instant getCreatedAt() { return createdAt; }
}
