package com.ownpic.image.adapter;

import com.ownpic.image.port.WatermarkDecoderPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!onnx")
public class NoOpWatermarkDecoderAdapter implements WatermarkDecoderPort {

    @Override
    public DecodeResult decode(byte[] imageBytes) {
        return DecodeResult.notDetected();
    }
}
