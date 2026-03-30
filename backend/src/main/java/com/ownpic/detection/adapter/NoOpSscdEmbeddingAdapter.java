package com.ownpic.detection.adapter;

import com.ownpic.detection.port.SscdEmbeddingPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!onnx")
public class NoOpSscdEmbeddingAdapter implements SscdEmbeddingPort {

    @Override
    public float[] generateEmbedding(byte[] imageBytes) {
        return null;
    }
}
