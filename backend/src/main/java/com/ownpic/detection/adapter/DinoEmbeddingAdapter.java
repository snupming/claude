package com.ownpic.detection.adapter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ownpic.detection.exception.MlEngineUnavailableException;
import com.ownpic.detection.port.DinoEmbeddingPort;
import com.ownpic.shared.ml.OnnxModelResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;

@Component
@Profile("onnx")
class DinoEmbeddingAdapter implements DinoEmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(DinoEmbeddingAdapter.class);
    private static final int INPUT_SIZE = 252;
    private static final String CLASSPATH_MODEL = "models/dinov2/dinov2_vits14_cls.onnx";

    private final DinoPreprocessor preprocessor;

    @Value("${ownpic.dinov2.model-path:}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private String outputName;
    private boolean available;

    DinoEmbeddingAdapter(DinoPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    @PostConstruct
    void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            var opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(Math.min(Runtime.getRuntime().availableProcessors(), 4));

            String resolvedPath = OnnxModelResolver.resolve(modelPath, CLASSPATH_MODEL, "DINOv2");
            session = env.createSession(resolvedPath, opts);

            inputName = session.getInputNames().iterator().next();
            outputName = session.getOutputNames().iterator().next();
            available = true;
            log.info("[DINOv2] Model loaded");
        } catch (Exception e) {
            available = false;
            log.error("[DINOv2] Model load failed (adapter disabled): {}", e.getMessage());
        }
    }

    @PreDestroy
    void destroy() {
        try { if (session != null) session.close(); } catch (OrtException ignored) {}
    }

    @Override
    public float[] generateEmbedding(byte[] imageBytes) {
        if (!available) {
            log.warn("[DINOv2] Adapter disabled, returning null");
            return null;
        }
        try {
            float[] tensor = preprocessor.preprocess(imageBytes);
            return infer(tensor);
        } catch (IOException e) {
            throw new MlEngineUnavailableException("DINOv2 preprocessing failed: " + e.getMessage());
        }
    }

    private float[] infer(float[] preprocessed) {
        try {
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(preprocessed), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
            try (var results = session.run(Map.of(inputName, inputTensor))) {
                OnnxValue output = results.get(outputName).orElseThrow(
                        () -> new OrtException("Output tensor not found"));
                return ((float[][]) output.getValue())[0];
            } finally {
                inputTensor.close();
            }
        } catch (OrtException e) {
            throw new MlEngineUnavailableException("DINOv2 inference failed: " + e.getMessage());
        }
    }
}
