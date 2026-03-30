package com.ownpic.evidence.adapter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ownpic.evidence.port.WatermarkDecoderPort;
import com.ownpic.image.adapter.TrustMarkPayloadBuilder;
import com.ownpic.shared.ml.OnnxModelResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * TrustMark ONNX Runtime 워터마크 디코더.
 *
 * <p>도용 이미지에서 100비트 페이로드를 추출합니다.
 * 인코더와 동일한 전처리(progressive downscale → 256×256 → [-1,1] NCHW)를 적용합니다.
 *
 * <h3>입출력</h3>
 * <ul>
 *   <li>입력: {@code [1, 3, 256, 256]} float32 NCHW [-1,1]</li>
 *   <li>출력: {@code float[1][100]} → threshold 0.5 → "0"/"1" 문자열</li>
 * </ul>
 *
 * <h3>검출 판정</h3>
 * <p>디코딩된 100비트 페이로드의 CRC-12 checksum을 검증합니다.
 * checksum 일치 시 {@code detected=true} (증거 강도 CONCLUSIVE),
 * 불일치 시 {@code detected=false} (CIRCUMSTANTIAL).
 *
 * <h3>PoC 검증 (TrustMarkOnnxPoc)</h3>
 * <p>비트 정확도: 100/100 (100.0%), 디코딩 지연: ~116ms (CPU).
 */
@Component
class TrustMarkDecoderAdapter implements WatermarkDecoderPort {

    private static final Logger log = LoggerFactory.getLogger(TrustMarkDecoderAdapter.class);

    private static final int TM_SIZE = 256;
    private static final int PAYLOAD_BITS = 100;
    private static final float BIT_THRESHOLD = 0.5f;
    private static final String CLASSPATH_MODEL = "models/trustmark/decoder_Q.onnx";

    @Value("${ownpic.trustmark.decoder-path:}")
    private String decoderPath;

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private String outputName;

    @PostConstruct
    void init() {
        try {
            long start = System.currentTimeMillis();
            env = OrtEnvironment.getEnvironment();

            var opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(Math.min(Runtime.getRuntime().availableProcessors(), 4));

            String resolvedPath = OnnxModelResolver.resolve(decoderPath, CLASSPATH_MODEL, "TrustMark-Decoder");
            session = env.createSession(resolvedPath, opts);

            inputName = session.getInputNames().iterator().next();
            outputName = session.getOutputNames().iterator().next();

            long elapsed = System.currentTimeMillis() - start;
            log.info("[TrustMark-Decoder] Loaded in {}ms. Input: {} → Output: {}",
                    elapsed, inputName, outputName);
        } catch (Exception e) {
            log.error("[TrustMark-Decoder] Failed to load model", e);
            throw new RuntimeException("TrustMark decoder load failed: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void destroy() {
        try {
            if (session != null) session.close();
        } catch (OrtException e) {
            log.warn("[TrustMark-Decoder] Error closing session", e);
        }
    }

    @Override
    public DecodeResult decode(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                log.warn("[TrustMark-Decoder] Failed to decode image bytes");
                return DecodeResult.notDetected();
            }

            long start = System.currentTimeMillis();

            float[] imgTensor = preprocess(image);
            float[] rawPayload = infer(imgTensor);

            // float → bit string (threshold 0.5)
            var bits = new StringBuilder(PAYLOAD_BITS);
            for (int i = 0; i < PAYLOAD_BITS; i++) {
                bits.append(rawPayload[i] > BIT_THRESHOLD ? '1' : '0');
            }
            String payload = bits.toString();

            // checksum 검증
            boolean checksumValid = TrustMarkPayloadBuilder.verifyChecksum(payload);

            long elapsed = System.currentTimeMillis() - start;

            if (checksumValid) {
                long userId = TrustMarkPayloadBuilder.extractUserId(payload);
                long imageId = TrustMarkPayloadBuilder.extractImageId(payload);
                log.info("[TrustMark-Decoder] Detected: userId={}, imageId={}, {}ms",
                        userId, imageId, elapsed);
                return DecodeResult.detected(payload);
            } else {
                log.debug("[TrustMark-Decoder] No valid watermark (checksum mismatch), {}ms", elapsed);
                return DecodeResult.notDetected();
            }

        } catch (Exception e) {
            log.warn("[TrustMark-Decoder] Decode failed: {}", e.getMessage());
            return DecodeResult.notDetected();
        }
    }

    // ==================================================================
    // ONNX 추론
    // ==================================================================

    private float[] infer(float[] preprocessed) {
        long[] shape = {1, 3, TM_SIZE, TM_SIZE};

        try {
            var inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(preprocessed), shape);
            try (var results = session.run(Map.of(inputName, inputTensor))) {
                float[][] decoded = (float[][]) results.get(outputName)
                        .orElseThrow(() -> new OrtException("Output tensor '" + outputName + "' not found"))
                        .getValue();
                return decoded[0]; // [1][100] → [100]
            } finally {
                inputTensor.close();
            }
        } catch (OrtException e) {
            throw new RuntimeException("TrustMark decoder inference failed: " + e.getMessage(), e);
        }
    }

    // ==================================================================
    // 전처리: 이미지 → [1,3,256,256] float32 [-1,1]
    // 인코더(TrustMarkWatermarkAdapter)와 동일한 로직.
    // ==================================================================

    private float[] preprocess(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        // Center crop (aspect > 2.0인 경우)
        if (h > w * 2 || w > h * 2) {
            int m = Math.min(w, h);
            int xOff = (w > h) ? (w - m) / 2 : 0;
            int yOff = (h > w) ? (h - m) / 2 : 0;
            img = img.getSubimage(xOff, yOff, m, m);
            w = m;
            h = m;
        }

        // Progressive downscale
        BufferedImage current = img;
        int cw = w, ch = h;
        while (cw > TM_SIZE * 2 || ch > TM_SIZE * 2) {
            int nw = Math.max(TM_SIZE, cw / 2);
            int nh = Math.max(TM_SIZE, ch / 2);
            BufferedImage step = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = step.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, nw, nh, null);
            g.dispose();
            current = step;
            cw = nw;
            ch = nh;
        }

        // 최종 256×256
        BufferedImage resized = new BufferedImage(TM_SIZE, TM_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(current, 0, 0, TM_SIZE, TM_SIZE, null);
        g.dispose();

        // NCHW [0,255] → [0,1] → [-1,1]
        float[] result = new float[3 * TM_SIZE * TM_SIZE];
        int idx = 0;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < TM_SIZE; y++) {
                for (int x = 0; x < TM_SIZE; x++) {
                    int rgb = resized.getRGB(x, y);
                    float val = switch (c) {
                        case 0 -> ((rgb >> 16) & 0xFF) / 255.0f;
                        case 1 -> ((rgb >> 8) & 0xFF) / 255.0f;
                        default -> (rgb & 0xFF) / 255.0f;
                    };
                    result[idx++] = val * 2.0f - 1.0f;
                }
            }
        }
        return result;
    }
}
