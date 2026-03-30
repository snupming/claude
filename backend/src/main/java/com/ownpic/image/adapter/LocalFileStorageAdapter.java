package com.ownpic.image.adapter;

import com.ownpic.image.port.ImageStoragePort;
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
            return filePath.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public byte[] load(String path) {
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 읽을 수 없습니다.", e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제에 실패했습니다.", e);
        }
    }
}
