package com.ownpic.image.domain;

import com.ownpic.auth.domain.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "images")
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "file_size", nullable = false)
    private int fileSize;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageStatus status = ImageStatus.PENDING;

    @Column(name = "gcs_path", length = 500)
    private String gcsPath;

    @Column(columnDefinition = "bytea")
    private byte[] embedding;

    @Column(name = "embedding_dino", columnDefinition = "bytea")
    private byte[] embeddingDino;

    @Column(name = "watermark_payload", length = 200)
    private String watermarkPayload;

    @Column(name = "watermark_width")
    private Integer watermarkWidth;

    @Column(name = "watermark_height")
    private Integer watermarkHeight;

    @Column(length = 500)
    private String keywords;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType = SourceType.UPLOAD;

    @Column(name = "source_platform", length = 20)
    private String sourcePlatform;

    @Column(name = "source_product_id", length = 200)
    private String sourceProductId;

    @Column(name = "source_image_url", length = 1000)
    private String sourceImageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters & Setters
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public int getFileSize() { return fileSize; }
    public void setFileSize(int fileSize) { this.fileSize = fileSize; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public ImageStatus getStatus() { return status; }
    public void setStatus(ImageStatus status) { this.status = status; }

    public String getGcsPath() { return gcsPath; }
    public void setGcsPath(String gcsPath) { this.gcsPath = gcsPath; }

    public byte[] getEmbedding() { return embedding; }
    public void setEmbedding(byte[] embedding) { this.embedding = embedding; }

    public byte[] getEmbeddingDino() { return embeddingDino; }
    public void setEmbeddingDino(byte[] embeddingDino) { this.embeddingDino = embeddingDino; }

    public String getWatermarkPayload() { return watermarkPayload; }
    public void setWatermarkPayload(String watermarkPayload) { this.watermarkPayload = watermarkPayload; }

    public Integer getWatermarkWidth() { return watermarkWidth; }
    public void setWatermarkWidth(Integer watermarkWidth) { this.watermarkWidth = watermarkWidth; }

    public Integer getWatermarkHeight() { return watermarkHeight; }
    public void setWatermarkHeight(Integer watermarkHeight) { this.watermarkHeight = watermarkHeight; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getSourcePlatform() { return sourcePlatform; }
    public void setSourcePlatform(String sourcePlatform) { this.sourcePlatform = sourcePlatform; }

    public String getSourceProductId() { return sourceProductId; }
    public void setSourceProductId(String sourceProductId) { this.sourceProductId = sourceProductId; }

    public String getSourceImageUrl() { return sourceImageUrl; }
    public void setSourceImageUrl(String sourceImageUrl) { this.sourceImageUrl = sourceImageUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
}
