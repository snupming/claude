package com.ownpic.shared.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ONNX 모델 경로 해석 유틸리티.
 * 설정된 경로 우선, 없으면 classpath 폴백.
 */
public final class OnnxModelResolver {

    private static final Logger log = LoggerFactory.getLogger(OnnxModelResolver.class);

    private OnnxModelResolver() {}

    public static String resolve(String configuredPath, String classpathDefault, String modelName) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath);
            if (Files.exists(path)) {
                log.info("[{}] Using configured model: {}", modelName, configuredPath);
                return configuredPath;
            }
            log.warn("[{}] Configured path not found: {}, falling back to classpath", modelName, configuredPath);
        }

        try {
            var resource = OnnxModelResolver.class.getClassLoader().getResource(classpathDefault);
            if (resource != null) {
                String resolvedPath = resource.toURI().getPath();
                log.info("[{}] Using classpath model: {}", modelName, resolvedPath);
                return resolvedPath;
            }
        } catch (Exception e) {
            log.warn("[{}] Classpath resolution failed: {}", modelName, e.getMessage());
        }

        throw new IllegalStateException(
                "[" + modelName + "] Model not found. Set path or place at classpath: " + classpathDefault);
    }
}
