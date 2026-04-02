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

    // 판매자/침해자 정보
    @Column(name = "platform_type", length = 50)
    private String platformType;

    @Column(name = "seller_name", length = 200)
    private String sellerName;

    @Column(name = "business_reg_number", length = 20)
    private String businessRegNumber;

    @Column(name = "representative_name", length = 100)
    private String representativeName;

    @Column(name = "business_address", length = 500)
    private String businessAddress;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    @Column(name = "store_url", length = 500)
    private String storeUrl;

    @Column(name = "best_guess_label", length = 200)
    private String bestGuessLabel;

    @Column(name = "detected_entity", length = 200)
    private String detectedEntity;

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

    public String getPlatformType() { return platformType; }
    public String getSellerName() { return sellerName; }
    public String getBusinessRegNumber() { return businessRegNumber; }
    public String getRepresentativeName() { return representativeName; }
    public String getBusinessAddress() { return businessAddress; }
    public String getContactPhone() { return contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public String getStoreUrl() { return storeUrl; }

    public void setPlatformType(String platformType) { this.platformType = platformType; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public void setBusinessRegNumber(String businessRegNumber) { this.businessRegNumber = businessRegNumber; }
    public void setRepresentativeName(String representativeName) { this.representativeName = representativeName; }
    public void setBusinessAddress(String businessAddress) { this.businessAddress = businessAddress; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public void setStoreUrl(String storeUrl) { this.storeUrl = storeUrl; }

    public String getBestGuessLabel() { return bestGuessLabel; }
    public String getDetectedEntity() { return detectedEntity; }
    public void setBestGuessLabel(String bestGuessLabel) { this.bestGuessLabel = bestGuessLabel; }
    public void setDetectedEntity(String detectedEntity) { this.detectedEntity = detectedEntity; }
}
