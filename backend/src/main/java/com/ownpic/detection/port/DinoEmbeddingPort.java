package com.ownpic.detection.port;

public interface DinoEmbeddingPort {
    float[] generateEmbedding(byte[] imageBytes);
}
