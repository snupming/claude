package com.ownpic.evidence.adapter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ownpic.image.adapter.TrustMarkPayloadBuilder;
import com.ownpic.image.port.WatermarkDecoderPort;
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
import java.nio.FloatBuffer;
import java.util.Map;

@Component
@Profile("onnx")
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
            env = OrtEnvironment.getEnvironment();
            var opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(Math.min(Runtime.getRuntime().availableProcessors(), 4));

            String resolvedPath = OnnxModelResolver.resolve(decoderPath, CLASSPATH_MODEL, "TrustMark-Decoder");
            session = env.createSession(resolvedPath, opts);

            inputName = session.getInputNames().iterator().next();
            outputName = session.getOutputNames().iterator().next();
            log.info("[TrustMark-Decoder] Loaded");
        } catch (Exception e) {
            throw new RuntimeException("TrustMark decoder load failed: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void destroy() {
        try { if (session != null) session.close(); } catch (OrtException ignored) {}
    }

    @Override
    public DecodeResult decode(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) return DecodeResult.notDetected();

            float[] imgTensor = preprocess(image);
            float[] rawPayload = infer(imgTensor);

            var bits = new StringBuilder(PAYLOAD_BITS);
            for (int i = 0; i < PAYLOAD_BITS; i++) {
                bits.append(rawPayload[i] > BIT_THRESHOLD ? '1' : '0');
            }
            String payload = bits.toString();

            if (TrustMarkPayloadBuilder.verifyChecksum(payload)) {
                long userId = TrustMarkPayloadBuilder.extractUserId(payload);
                long imageId = TrustMarkPayloadBuilder.extractImageId(payload);
                log.info("[TrustMark-Decoder] Detected: userId={}, imageId={}", userId, imageId);
                return DecodeResult.detected(payload);
            } else {
                return DecodeResult.notDetected();
            }
        } catch (Exception e) {
            log.warn("[TrustMark-Decoder] Decode failed: {}", e.getMessage());
            return DecodeResult.notDetected();
        }
    }

    private float[] infer(float[] preprocessed) {
        try {
            var inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(preprocessed), new long[]{1, 3, TM_SIZE, TM_SIZE});
            try (var results = session.run(Map.of(inputName, inputTensor))) {
                float[][] decoded = (float[][]) results.get(outputName)
                        .orElseThrow(() -> new OrtException("Output not found")).getValue();
                return decoded[0];
            } finally {
                inputTensor.close();
            }
        } catch (OrtException e) {
            throw new RuntimeException("TrustMark decoder inference failed: " + e.getMessage(), e);
        }
    }

    private float[] preprocess(BufferedImage img) {
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
}
