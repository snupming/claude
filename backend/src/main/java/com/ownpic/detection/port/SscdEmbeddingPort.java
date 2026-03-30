package com.ownpic.detection.port;

public interface SscdEmbeddingPort {
    float[] generateEmbedding(byte[] imageBytes);
}
