package com.ownpic.detection.adapter;

import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * DINOv2 전처리 파이프라인.
 * 순서: 이미지 디코딩 → 짧은 변 252로 리사이즈(종횡비 유지) → 252x252 CenterCrop → ImageNet 정규화 → NCHW float[]
 */
@Component
public class DinoPreprocessor {

    static final int INPUT_SIZE = 252;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    public float[] preprocess(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("Failed to decode image");
        }
        return preprocess(image);
    }

    public float[] preprocess(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        int newW, newH;
        if (w < h) {
            newW = INPUT_SIZE;
            newH = Math.round((float) h / w * INPUT_SIZE);
        } else {
            newH = INPUT_SIZE;
            newW = Math.round((float) w / h * INPUT_SIZE);
        }

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newW, newH, null);
        g.dispose();

        int xOff = (newW - INPUT_SIZE) / 2;
        int yOff = (newH - INPUT_SIZE) / 2;
        BufferedImage cropped = resized.getSubimage(xOff, yOff, INPUT_SIZE, INPUT_SIZE);

        float[] result = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int idx = 0;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < INPUT_SIZE; y++) {
                for (int x = 0; x < INPUT_SIZE; x++) {
                    int rgb = cropped.getRGB(x, y);
                    float value = switch (c) {
                        case 0 -> ((rgb >> 16) & 0xFF) / 255.0f;
                        case 1 -> ((rgb >> 8) & 0xFF) / 255.0f;
                        default -> (rgb & 0xFF) / 255.0f;
                    };
                    result[idx++] = (value - MEAN[c]) / STD[c];
                }
            }
        }
        return result;
    }
}
