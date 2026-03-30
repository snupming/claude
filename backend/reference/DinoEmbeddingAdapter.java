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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * DINOv2-ViT-S/14 ONNX Runtime 임베딩 생성기.
 *
 * <p>모델: dinov2_vits14_cls.onnx (CLS token, 384-dim, L2-normalized).
 * Apache-2.0 License (Meta).
 *
 * <h3>PoC v4 결과</h3>
 * <ul>
 *   <li>bg_swap_gradient: 0.913 (SSCD 0.431)</li>
 *   <li>bg_swap_indoor: 0.906 (SSCD 0.715)</li>
 *   <li>추론 속도: ~62ms (CPU)</li>
 *   <li>모델 크기: ~85MB</li>
 * </ul>
 *
 * <p>듀얼 판정: SSCD &gt;= 0.30 OR DINOv2 &gt;= 0.70 → recall 100%, FPR 0%.
 */
@Component
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

    DinoEmbeddingAdapter(DinoPreprocessor preprocessor) {
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

            String resolvedPath = OnnxModelResolver.resolve(modelPath, CLASSPATH_MODEL, "DINOv2");
            session = env.createSession(resolvedPath, sessionOptions);

            inputName = session.getInputNames().iterator().next();
            outputName = session.getOutputNames().iterator().next();

            long elapsed = System.currentTimeMillis() - start;
            log.info("[DINOv2] Model loaded in {}ms. Input: {}, Output: {}",
                    elapsed, inputName, outputName);
        } catch (Exception e) {
            log.error("[DINOv2] Failed to load model", e);
            throw new MlEngineUnavailableException("DINOv2 model load failed: " + e.getMessage());
        }
    }

    @PreDestroy
    void destroy() {
        try {
            if (session != null) session.close();
        } catch (OrtException e) {
            log.warn("[DINOv2] Error closing session", e);
        }
    }

    @Override
    public float[] generateEmbedding(byte[] imageBytes) {
        try {
            float[] tensor = preprocessor.preprocess(imageBytes);
            return infer(tensor);
        } catch (IOException e) {
            throw new MlEngineUnavailableException("DINOv2 image preprocessing failed: " + e.getMessage());
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
                return embeddings[0]; // [1, 384] → [384]
            } finally {
                inputTensor.close();
            }
        } catch (OrtException e) {
            throw new MlEngineUnavailableException("DINOv2 inference failed: " + e.getMessage());
        }
    }
}
