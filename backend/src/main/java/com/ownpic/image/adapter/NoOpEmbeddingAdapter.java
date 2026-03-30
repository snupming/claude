package com.ownpic.image.adapter;

import com.ownpic.image.port.EmbeddingPort;
import org.springframework.stereotype.Component;

@Component
public class NoOpEmbeddingAdapter implements EmbeddingPort {

    @Override
    public byte[] embed(byte[] imageBytes) {
        // NoOp: null 반환 (실제 SSCD 어댑터로 교체 필요)
        return null;
    }
}
