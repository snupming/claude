package com.ownpic.detection.port;

import java.util.List;
import java.util.UUID;

public interface SimilarImageSearchPort {
    List<SimilarImage> findByUser(float[] embedding, UUID userId, double threshold, int limit);
    List<SimilarImage> findAll(float[] embedding, double threshold, int limit);
    List<SimilarImage> findByUserDino(float[] embedding, UUID userId, double threshold, int limit);
    List<SimilarImage> findAllDino(float[] embedding, double threshold, int limit);

    record SimilarImage(Long id, UUID userId, double similarity) {}
}
