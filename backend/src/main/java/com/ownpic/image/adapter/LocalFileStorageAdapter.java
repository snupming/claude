package com.ownpic.image.adapter;

import com.ownpic.image.port.ImageStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@EnableConfigurationProperties(StorageProperties.class)
public class LocalFileStorageAdapter implements ImageStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    private final Path basePath;

    public LocalFileStorageAdapter(StorageProperties properties) {
        this.basePath = Path.of(properties.basePath() != null ? properties.basePath() : "./uploads");
    }

    @Override
    public String store(UUID userId, Long imageId, byte[] imageBytes, String extension) {
        try {
            var dir = basePath.resolve(userId.toString());
            Files.createDirectories(dir);
            var filePath = dir.resolve(imageId + "." + extension);
            Files.write(filePath, imageBytes);
            return userId + "/" + imageId + "." + extension;
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public String save(UUID userId, byte[] imageBytes) {
        try {
            var dir = basePath.resolve(userId.toString());
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + ".png";
            var filePath = dir.resolve(filename);
            Files.write(filePath, imageBytes);
            return userId + "/" + filename;
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public byte[] load(String path) {
        try {
            return Files.readAllBytes(basePath.resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 읽을 수 없습니다.", e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(basePath.resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제에 실패했습니다.", e);
        }
    }

    @Override
    public void deleteQuietly(String path) {
        try {
            Files.deleteIfExists(basePath.resolve(path));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", path, e);
        }
    }
}
