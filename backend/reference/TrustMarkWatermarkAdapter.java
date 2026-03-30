package com.ownpic.image.adapter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ownpic.image.port.WatermarkException;
import com.ownpic.image.port.WatermarkPort;
import com.ownpic.image.port.WatermarkResult;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;

    /**
     * TrustMark ONNX Runtime 워터마크 인코더.
     *
     * <h3>PoC 검증 결과</h3>
     * <ul>
     *   <li>비트 정확도: 100/100 (100.0% 라운드트립)</li>
     *   <li>PSNR: 43.20 dB (progressive downscale 필수)</li>
     *   <li>인코딩 지연: ~399ms (CPU)</li>
     *   <li>모델: variant Q (encoder 16.5MB)</li>
     * </ul>
     *
     * <p>TODO: 현재 256×256 고정 출력. 원본 해상도 복원(잔차 업스케일)은
     * MVP 이후 구현 예정. 설계서 섹션 2.2 참조.
     */
@Component
class TrustMarkWatermarkAdapter implements WatermarkPort {

    private static final Logger log = LoggerFactory.getLogger(TrustMarkWatermarkAdapter.class);

    private static final int TM_SIZE = 256;
    private static final int PAYLOAD_BITS = 100;
    private static final String CLASSPATH_MODEL = "models/trustmark/encoder_Q.onnx";

    @Value("${ownpic.trustmark.encoder-path:}")
    private String encoderPath;

    private OrtEnvironment env;
    private OrtSession session;
    private String inputImageName;
    private String inputPayloadName;
    private String outputName;

    @PostConstruct
    void init() {
        try {
            long start = System.currentTimeMillis();
            env = OrtEnvironment.getEnvironment();

            var opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(Math.min(Runtime.getRuntime().availableProcessors(), 4));

            String resolvedPath = OnnxModelResolver.resolve(encoderPath, CLASSPATH_MODEL, "TrustMark");
            session = env.createSession(resolvedPath, opts);

            var inputNames = session.getInputNames().iterator();
            inputImageName = inputNames.next();
            inputPayloadName = inputNames.next();
            outputName = session.getOutputNames().iterator().next();

            long elapsed = System.currentTimeMillis() - start;
            log.info("[TrustMark] Encoder loaded in {}ms. Inputs: {}, {} → {}",
                    elapsed, inputImageName, inputPayloadName, outputName);
        } catch (Exception e) {
            log.error("[TrustMark] Failed to load encoder model", e);
            throw new WatermarkException("TrustMark encoder load failed: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void destroy() {
        try {
            if (session != null) session.close();
        } catch (OrtException e) {
            log.warn("[TrustMark] Error closing encoder session", e);
        }
    }

    @Override
    public WatermarkResult encode(byte[] imageBytes, String payload) {
        if (payload == null || payload.length() != PAYLOAD_BITS) {
            throw new WatermarkException(
                    "Payload must be exactly " + PAYLOAD_BITS + " bits, got: "
                            + (payload == null ? "null" : payload.length()));
        }

        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                throw new WatermarkException("Failed to decode image");
            }

            float[] imgTensor = preprocessTrustMark(original);

            float[] payloadTensor = new float[PAYLOAD_BITS];
            for (int i = 0; i < PAYLOAD_BITS; i++) {
                payloadTensor[i] = payload.charAt(i) == '1' ? 1.0f : 0.0f;
            }

            float[] encodedFlat = infer(imgTensor, payloadTensor);

            BufferedImage watermarked = postprocess(encodedFlat);
            byte[] outputBytes = toPng(watermarked);

            log.debug("[TrustMark] Encoded: {}x{} → {}x{}, payload={}..., {} bytes",
                    original.getWidth(), original.getHeight(),
                    TM_SIZE, TM_SIZE,
                    payload.substring(0, 10),
                    outputBytes.length);

            return new WatermarkResult(outputBytes, TM_SIZE, TM_SIZE, payload);

        } catch (WatermarkException e) {
            throw e;
        } catch (Exception e) {
            throw new WatermarkException("TrustMark encoding failed: " + e.getMessage(), e);
        }
    }

    // ==================================================================
    // ONNX 추론
    // ==================================================================

    private float[] infer(float[] imgTensor, float[] payloadTensor) {
        try {
            var imgOnnx = OnnxTensor.createTensor(env,
                    FloatBuffer.wrap(imgTensor), new long[]{1, 3, TM_SIZE, TM_SIZE});
            var payOnnx = OnnxTensor.createTensor(env,
                    FloatBuffer.wrap(payloadTensor), new long[]{1, PAYLOAD_BITS});

            try (var result = session.run(Map.of(inputImageName, imgOnnx, inputPayloadName, payOnnx))) {
                float[][][][] encoded = (float[][][][]) result.get(outputName).orElseThrow(
                        () -> new OrtException("Output tensor not found")).getValue();
                return flatten4d(encoded);
            } finally {
                imgOnnx.close();
                payOnnx.close();
            }
        } catch (OrtException e) {
            throw new WatermarkException("ONNX inference failed: " + e.getMessage(), e);
        }
    }

    // ==================================================================
    // 전처리: 이미지 → [1,3,256,256] float32 [-1,1]
    // ==================================================================

    private float[] preprocessTrustMark(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        if (h > w * 2 || w > h * 2) {
            int m = Math.min(w, h);
            int xOff = (w > h) ? (w - m) / 2 : 0;
            int yOff = (h > w) ? (h - m) / 2 : 0;
            img = img.getSubimage(xOff, yOff, m, m);
            w = m;
            h = m;
        }

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

        BufferedImage resized = new BufferedImage(TM_SIZE, TM_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(current, 0, 0, TM_SIZE, TM_SIZE, null);
        g.dispose();

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

    // ==================================================================
    // 후처리: [-1,1] NCHW float[] → BufferedImage
    // ==================================================================

    private BufferedImage postprocess(float[] encoded) {
        BufferedImage img = new BufferedImage(TM_SIZE, TM_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < TM_SIZE; y++) {
            for (int x = 0; x < TM_SIZE; x++) {
                int rIdx = 0 * TM_SIZE * TM_SIZE + y * TM_SIZE + x;
                int gIdx = 1 * TM_SIZE * TM_SIZE + y * TM_SIZE + x;
                int bIdx = 2 * TM_SIZE * TM_SIZE + y * TM_SIZE + x;

                int r = clamp((encoded[rIdx] + 1.0f) / 2.0f * 255.0f);
                int gVal = clamp((encoded[gIdx] + 1.0f) / 2.0f * 255.0f);
                int b = clamp((encoded[bIdx] + 1.0f) / 2.0f * 255.0f);

                img.setRGB(x, y, (r << 16) | (gVal << 8) | b);
            }
        }
        return img;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private byte[] toPng(BufferedImage img) throws IOException {
        var bos = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "PNG", bos)) {
            throw new IOException("PNG writer not available");
        }
        return bos.toByteArray();
    }

    private static float[] flatten4d(float[][][][] arr) {
        int d0 = arr.length, d1 = arr[0].length, d2 = arr[0][0].length, d3 = arr[0][0][0].length;
        float[] flat = new float[d0 * d1 * d2 * d3];
        int idx = 0;
        for (int i = 0; i < d0; i++)
            for (int j = 0; j < d1; j++)
                for (int k = 0; k < d2; k++)
                    for (int l = 0; l < d3; l++)
                        flat[idx++] = arr[i][j][k][l];
        return flat;
    }
}
