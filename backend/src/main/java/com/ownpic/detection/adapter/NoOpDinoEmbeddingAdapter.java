package com.ownpic.detection.adapter;

import com.ownpic.detection.port.DinoEmbeddingPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!onnx")
public class NoOpDinoEmbeddingAdapter implements DinoEmbeddingPort {

    @Override
    public float[] generateEmbedding(byte[] imageBytes) {
        return null;
    }
}
