package com.ownpic.image.adapter;

import com.ownpic.image.port.WatermarkPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
@Profile("!onnx")
public class NoOpWatermarkAdapter implements WatermarkPort {

    @Override
    public WatermarkResult encode(byte[] imageBytes, String payload) {
        int width = 0, height = 0;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img != null) {
                width = img.getWidth();
                height = img.getHeight();
            }
        } catch (IOException ignored) {
        }
        return new WatermarkResult(imageBytes, width, height, payload);
    }
}
