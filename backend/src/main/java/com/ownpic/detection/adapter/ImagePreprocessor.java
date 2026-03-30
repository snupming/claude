package com.ownpic.detection.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * SSCD 전처리 파이프라인.
 * 순서: 이미지 디코딩 → 패딩 트리밍 → 320x320 square resize → ImageNet 정규화 → NCHW float[]
 */
@Component
public class ImagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ImagePreprocessor.class);

    private static final int INPUT_SIZE = 320;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    private static final int TRIM_TOLERANCE = 30;

    public float[] preprocess(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("Failed to decode image");
        }
        return preprocess(image);
    }

    public float[] preprocess(BufferedImage image) {
        BufferedImage trimmed = trimPadding(image);

        BufferedImage resized = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(trimmed, 0, 0, INPUT_SIZE, INPUT_SIZE, null);
        g.dispose();

        float[] result = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int idx = 0;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < INPUT_SIZE; y++) {
                for (int x = 0; x < INPUT_SIZE; x++) {
                    int rgb = resized.getRGB(x, y);
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

    static BufferedImage trimPadding(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        if (w < 4 || h < 4) return image;

        int minW = Math.max(4, w / 10);
        int minH = Math.max(4, h / 10);

        int[] corners = {
                image.getRGB(0, 0),
                image.getRGB(w - 1, 0),
                image.getRGB(0, h - 1),
                image.getRGB(w - 1, h - 1)
        };

        int borderColor = mostCommonColor(corners);
        int bR = (borderColor >> 16) & 0xFF;
        int bG = (borderColor >> 8) & 0xFF;
        int bB = borderColor & 0xFF;

        int top = 0;
        while (top < h - minH && isUniformRow(image, top, w, bR, bG, bB)) top++;

        int bottom = h - 1;
        while (bottom > top + minH && isUniformRow(image, bottom, w, bR, bG, bB)) bottom--;

        int left = 0;
        while (left < w - minW && isUniformCol(image, left, top, bottom, bR, bG, bB)) left++;

        int right = w - 1;
        while (right > left + minW && isUniformCol(image, right, top, bottom, bR, bG, bB)) right--;

        int trimmedW = right - left + 1;
        int trimmedH = bottom - top + 1;
        if (trimmedW >= w * 0.95 && trimmedH >= h * 0.95) {
            return image;
        }

        return image.getSubimage(left, top, trimmedW, trimmedH);
    }

    private static boolean isUniformRow(BufferedImage img, int y, int w, int bR, int bG, int bB) {
        int step = Math.max(1, w / 80);
        int tolSq = TRIM_TOLERANCE * TRIM_TOLERANCE;
        for (int x = 0; x < w; x += step) {
            int rgb = img.getRGB(x, y);
            int dR = ((rgb >> 16) & 0xFF) - bR;
            int dG = ((rgb >> 8) & 0xFF) - bG;
            int dB = (rgb & 0xFF) - bB;
            if (dR * dR + dG * dG + dB * dB > tolSq) return false;
        }
        return true;
    }

    private static boolean isUniformCol(BufferedImage img, int x, int top, int bottom,
                                         int bR, int bG, int bB) {
        int h = bottom - top + 1;
        int step = Math.max(1, h / 80);
        int tolSq = TRIM_TOLERANCE * TRIM_TOLERANCE;
        for (int y = top; y <= bottom; y += step) {
            int rgb = img.getRGB(x, y);
            int dR = ((rgb >> 16) & 0xFF) - bR;
            int dG = ((rgb >> 8) & 0xFF) - bG;
            int dB = (rgb & 0xFF) - bB;
            if (dR * dR + dG * dG + dB * dB > tolSq) return false;
        }
        return true;
    }

    private static int mostCommonColor(int[] colors) {
        int[] counts = new int[colors.length];
        int tolSq = TRIM_TOLERANCE * TRIM_TOLERANCE;
        for (int i = 0; i < colors.length; i++) {
            for (int j = 0; j < colors.length; j++) {
                int dR = ((colors[i] >> 16) & 0xFF) - ((colors[j] >> 16) & 0xFF);
                int dG = ((colors[i] >> 8) & 0xFF) - ((colors[j] >> 8) & 0xFF);
                int dB = (colors[i] & 0xFF) - (colors[j] & 0xFF);
                if (dR * dR + dG * dG + dB * dB <= tolSq) counts[i]++;
            }
        }
        int maxIdx = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[maxIdx]) maxIdx = i;
        }
        return colors[maxIdx];
    }
}
