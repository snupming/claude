package com.ownpic.detection.adapter;

import com.ownpic.detection.port.SimilarImageSearchPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("!pgvector")
public class NoOpSimilarImageSearchAdapter implements SimilarImageSearchPort {

    @Override
    public List<SimilarImage> findByUser(float[] embedding, UUID userId, double threshold, int limit) {
        return List.of();
    }

    @Override
    public List<SimilarImage> findAll(float[] embedding, double threshold, int limit) {
        return List.of();
    }

    @Override
    public List<SimilarImage> findByUserDino(float[] embedding, UUID userId, double threshold, int limit) {
        return List.of();
    }

    @Override
    public List<SimilarImage> findAllDino(float[] embedding, double threshold, int limit) {
        return List.of();
    }
}
