package com.ownpic.detection.adapter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ownpic.detection.exception.MlEngineUnavailableException;
import com.ownpic.detection.port.SscdEmbeddingPort;
import com.ownpic.shared.ml.OnnxModelResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * SSCD ONNX Runtime 임베딩 생성기.
 *
 * <p>모델: sscd_disc_mixup (ResNet50, 512-dim, MIT License).
 */
@Component
class SscdEmbeddingAdapter implements SscdEmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(SscdEmbeddingAdapter.class);
    private static final int INPUT_SIZE = 320;
    private static final String CLASSPATH_MODEL = "models/sscd/sscd.onnx";

    private final ImagePreprocessor preprocessor;

    @Value("${ownpic.sscd.model-path:}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private String outputName;

    SscdEmbeddingAdapter(ImagePreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    @PostConstruct
    void init() {
        try {
            long start = System.currentTimeMillis();
            env = OrtEnvironment.getEnvironment();

            var sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            sessionOptions.setIntraOpNumThreads(
                    Math.min(Runtime.getRuntime().availableProcessors(), 4));

            String resolvedPath = OnnxModelResolver.resolve(modelPath, CLASSPATH_MODEL, "SSCD");
            session = env.createSession(resolvedPath, sessionOptions);

            inputName = session.getInputNames().iterator().next();
            outputName = session.getOutputNames().iterator().next();

            long elapsed = System.currentTimeMillis() - start;
            log.info("[SSCD] Model loaded in {}ms. Input: {}, Output: {}",
                    elapsed, inputName, outputName);
        } catch (Exception e) {
            log.error("[SSCD] Failed to load model", e);
            throw new MlEngineUnavailableException("SSCD model load failed: " + e.getMessage());
        }
    }

    @PreDestroy
    void destroy() {
        try {
            if (session != null) session.close();
        } catch (OrtException e) {
            log.warn("[SSCD] Error closing session", e);
        }
    }

    @Override
    public float[] generateEmbedding(byte[] imageBytes) {
        try {
            float[] tensor = preprocessor.preprocess(imageBytes);
            return infer(tensor);
        } catch (IOException e) {
            throw new MlEngineUnavailableException("Image preprocessing failed: " + e.getMessage());
        }
    }

    private float[] infer(float[] preprocessed) {
        long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};

        try {
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(preprocessed), shape);
            try (var results = session.run(Map.of(inputName, inputTensor))) {
                OnnxValue output = results.get(outputName).orElseThrow(
                        () -> new OrtException("Output tensor '" + outputName + "' not found"));
                float[][] embeddings = (float[][]) output.getValue();
                return embeddings[0];
            } finally {
                inputTensor.close();
            }
        } catch (OrtException e) {
            throw new MlEngineUnavailableException("SSCD inference failed: " + e.getMessage());
        }
    }
}
