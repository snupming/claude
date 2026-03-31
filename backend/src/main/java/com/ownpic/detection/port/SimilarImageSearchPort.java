package com.ownpic.detection.port;

import java.util.List;
import java.util.UUID;

public interface SimilarImageSearchPort {
    List<SimilarImage> findByUser(float[] embedding, UUID userId, double threshold, int limit);
    List<SimilarImage> findAll(float[] embedding, double threshold, int limit);
    List<SimilarImage> findByUserDino(float[] embedding, UUID userId, double threshold, int limit);
    List<SimilarImage> findAllDino(float[] embedding, double threshold, int limit);

    /** 배치 SSCD 검색 (CROSS JOIN LATERAL) */
    List<BatchResult> findAllBatch(List<ImageEmbedding> embeddings, double threshold, int limitPerImage);
    /** 배치 DINOv2 검색 */
    List<BatchResult> findAllDinoBatch(List<ImageEmbedding> embeddings, double threshold, int limitPerImage);

    record SimilarImage(Long id, UUID userId, double similarity) {}
    record ImageEmbedding(Long imageId, float[] embedding) {}
    record BatchResult(Long sourceImageId, Long matchedImageId, UUID matchedUserId, double similarity) {}
}
