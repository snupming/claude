package com.ownpic.image.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class LocalFileStorageAdapterTest {

    @TempDir Path tempDir;
    LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalFileStorageAdapter(new StorageProperties(tempDir.toString()));
    }

    @Test
    void save_createsFileAndReturnsPath() {
        UUID userId = UUID.randomUUID();
        byte[] data = "image data".getBytes();

        String path = adapter.save(userId, data);

        assertThat(path).startsWith(userId.toString());
        assertThat(path).endsWith(".png");
        assertThat(Files.exists(tempDir.resolve(path))).isTrue();
    }

    @Test
    void store_createsFileWithExtension() {
        UUID userId = UUID.randomUUID();
        byte[] data = "image".getBytes();

        String path = adapter.store(userId, 42L, data, "jpg");

        assertThat(path).isEqualTo(userId + "/42.jpg");
        assertThat(Files.exists(tempDir.resolve(path))).isTrue();
    }

    @Test
    void load_existingFile_returnsBytes() {
        UUID userId = UUID.randomUUID();
        byte[] data = "test image content".getBytes();
        String path = adapter.save(userId, data);

        byte[] loaded = adapter.load(path);

        assertThat(loaded).isEqualTo(data);
    }

    @Test
    void load_nonExistentFile_throwsException() {
        assertThatThrownBy(() -> adapter.load("nonexistent/file.png"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void delete_existingFile_removesIt() {
        UUID userId = UUID.randomUUID();
        String path = adapter.save(userId, "data".getBytes());
        assertThat(Files.exists(tempDir.resolve(path))).isTrue();

        adapter.delete(path);

        assertThat(Files.exists(tempDir.resolve(path))).isFalse();
    }

    @Test
    void deleteQuietly_nonExistentFile_doesNotThrow() {
        assertThatCode(() -> adapter.deleteQuietly("non/existent.png"))
                .doesNotThrowAnyException();
    }

    @Test
    void save_multipleFiles_uniquePaths() {
        UUID userId = UUID.randomUUID();
        String path1 = adapter.save(userId, "img1".getBytes());
        String path2 = adapter.save(userId, "img2".getBytes());

        assertThat(path1).isNotEqualTo(path2);
    }
}
