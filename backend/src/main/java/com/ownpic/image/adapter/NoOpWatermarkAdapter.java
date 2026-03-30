package com.ownpic.image.adapter;

import com.ownpic.image.port.WatermarkPort;
import org.springframework.stereotype.Component;

@Component
public class NoOpWatermarkAdapter implements WatermarkPort {

    @Override
    public WatermarkResult encode(byte[] imageBytes, String payload) {
        // NoOp: 원본 이미지 그대로 반환 (실제 TrustMark 어댑터로 교체 필요)
        return new WatermarkResult(imageBytes, payload);
    }
}
