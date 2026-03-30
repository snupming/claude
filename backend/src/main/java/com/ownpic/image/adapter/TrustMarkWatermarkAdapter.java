package com.ownpic.image.adapter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ownpic.image.port.WatermarkException;
import com.ownpic.image.port.WatermarkPort;
import com.ownpic.shared.ml.OnnxModelResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;

@Component
@Profile("onnx")
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
    private boolean available;

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

            available = true;
            log.info("[TrustMark] Encoder loaded in {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            available = false;
            log.error("[TrustMark] Encoder load failed (adapter disabled): {}", e.getMessage());
        }
    }

    @PreDestroy
    void destroy() {
        try { if (session != null) session.close(); } catch (OrtException ignored) {}
    }

    @Override
    public WatermarkResult encode(byte[] imageBytes, String payload) {
        if (!available) {
            throw new WatermarkException("TrustMark encoder not available (native library load failed)");
        }
        if (payload == null || payload.length() != PAYLOAD_BITS) {
            throw new WatermarkException("Payload must be exactly " + PAYLOAD_BITS + " bits");
        }

        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) throw new WatermarkException("Failed to decode image");

            float[] imgTensor = preprocessTrustMark(original);
            float[] payloadTensor = new float[PAYLOAD_BITS];
            for (int i = 0; i < PAYLOAD_BITS; i++) {
                payloadTensor[i] = payload.charAt(i) == '1' ? 1.0f : 0.0f;
            }

            float[] encodedFlat = infer(imgTensor, payloadTensor);
            BufferedImage watermarked = postprocess(encodedFlat);
            byte[] outputBytes = toPng(watermarked);

            return new WatermarkResult(outputBytes, TM_SIZE, TM_SIZE, payload);
        } catch (WatermarkException e) {
            throw e;
        } catch (Exception e) {
            throw new WatermarkException("TrustMark encoding failed: " + e.getMessage(), e);
        }
    }

    private float[] infer(float[] imgTensor, float[] payloadTensor) {
        try {
            var imgOnnx = OnnxTensor.createTensor(env, FloatBuffer.wrap(imgTensor), new long[]{1, 3, TM_SIZE, TM_SIZE});
            var payOnnx = OnnxTensor.createTensor(env, FloatBuffer.wrap(payloadTensor), new long[]{1, PAYLOAD_BITS});

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

    private float[] preprocessTrustMark(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (h > w * 2 || w > h * 2) {
            int m = Math.min(w, h);
            img = img.getSubimage(w > h ? (w - m) / 2 : 0, h > w ? (h - m) / 2 : 0, m, m);
            w = m; h = m;
        }

        BufferedImage current = img;
        int cw = w, ch = h;
        while (cw > TM_SIZE * 2 || ch > TM_SIZE * 2) {
            int nw = Math.max(TM_SIZE, cw / 2), nh = Math.max(TM_SIZE, ch / 2);
            BufferedImage step = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = step.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, nw, nh, null); g.dispose();
            current = step; cw = nw; ch = nh;
        }

        BufferedImage resized = new BufferedImage(TM_SIZE, TM_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(current, 0, 0, TM_SIZE, TM_SIZE, null); g.dispose();

        float[] result = new float[3 * TM_SIZE * TM_SIZE];
        int idx = 0;
        for (int c = 0; c < 3; c++)
            for (int y = 0; y < TM_SIZE; y++)
                for (int x = 0; x < TM_SIZE; x++) {
                    int rgb = resized.getRGB(x, y);
                    float val = switch (c) {
                        case 0 -> ((rgb >> 16) & 0xFF) / 255.0f;
                        case 1 -> ((rgb >> 8) & 0xFF) / 255.0f;
                        default -> (rgb & 0xFF) / 255.0f;
                    };
                    result[idx++] = val * 2.0f - 1.0f;
                }
        return result;
    }

    private BufferedImage postprocess(float[] encoded) {
        BufferedImage img = new BufferedImage(TM_SIZE, TM_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < TM_SIZE; y++)
            for (int x = 0; x < TM_SIZE; x++) {
                int r = clamp((encoded[y * TM_SIZE + x] + 1.0f) / 2.0f * 255.0f);
                int g = clamp((encoded[TM_SIZE * TM_SIZE + y * TM_SIZE + x] + 1.0f) / 2.0f * 255.0f);
                int b = clamp((encoded[2 * TM_SIZE * TM_SIZE + y * TM_SIZE + x] + 1.0f) / 2.0f * 255.0f);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        return img;
    }

    private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }

    private byte[] toPng(BufferedImage img) throws IOException {
        var bos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", bos);
        return bos.toByteArray();
    }

    private static float[] flatten4d(float[][][][] arr) {
        int d0 = arr.length, d1 = arr[0].length, d2 = arr[0][0].length, d3 = arr[0][0][0].length;
        float[] flat = new float[d0 * d1 * d2 * d3];
        int idx = 0;
        for (float[][][] a : arr) for (float[][] b : a) for (float[] c : b) for (float v : c) flat[idx++] = v;
        return flat;
    }
}
