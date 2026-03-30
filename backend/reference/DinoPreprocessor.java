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
 * DINOv2 전처리 파이프라인.
 *
 * <p>순서: 이미지 디코딩 → 짧은 변 252로 리사이즈(종횡비 유지) → 252×252 CenterCrop → ImageNet 정규화 → NCHW float[].
 *
 * <p>PoC Python 코드의 torchvision.transforms와 동일:
 * <pre>
 * transforms.Resize(252),       # 짧은 변 252, 종횡비 유지
 * transforms.CenterCrop(252),   # 중앙 252×252
 * transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
 * </pre>
 *
 * <p>SSCD의 {@link ImagePreprocessor}와 다른 점:
 * <ul>
 *   <li>패딩 트리밍 없음 — DINOv2는 ViT 어텐션 기반이므로 패딩에 로버스트</li>
 *   <li>종횡비 유지 + CenterCrop — SSCD는 320×320 강제 스큐</li>
 *   <li>입력 크기 252 — DINOv2 ViT-S/14: 14px 패치 × 18 = 252</li>
 * </ul>
 */
@Component
class DinoPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(DinoPreprocessor.class);

    static final int INPUT_SIZE = 252;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    /**
     * 이미지 바이트 → DINOv2 입력 텐서 (NCHW float[]).
     */
    float[] preprocess(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("Failed to decode image");
        }
        return preprocess(image);
    }

    /**
     * BufferedImage → DINOv2 입력 텐서 (NCHW float[]).
     */
    float[] preprocess(BufferedImage image) {
        // Step 1: 짧은 변을 252로 리사이즈 (종횡비 유지)
        // torchvision.Resize(252)와 동일 — shorter edge = 252
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

        // Step 2: 252×252 CenterCrop
        int xOff = (newW - INPUT_SIZE) / 2;
        int yOff = (newH - INPUT_SIZE) / 2;
        BufferedImage cropped = resized.getSubimage(xOff, yOff, INPUT_SIZE, INPUT_SIZE);

        // Step 3: ImageNet 정규화 + NCHW 변환
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
