package com.ownpic.image;

import com.ownpic.detection.port.DinoEmbeddingPort;
import com.ownpic.detection.port.SscdEmbeddingPort;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.port.ImageStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageIndexingServiceTest {

    @Mock ImageRepository imageRepository;
    @Mock ImageStoragePort storagePort;
    @Mock SscdEmbeddingPort sscdPort;
    @Mock DinoEmbeddingPort dinoPort;

    @InjectMocks ImageIndexingService service;

    private Image image;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        image = new Image();
        image.setStatus(ImageStatus.PROTECTED);
        image.setGcsPath(userId + "/test.png");
    }

    @Test
    void onImageProtected_bothEmbeddingsSucceed_statusIndexed() {
        when(imageRepository.findById(1L)).thenReturn(Optional.of(image));
        when(storagePort.load(image.getGcsPath())).thenReturn(new byte[]{1, 2, 3});
        when(sscdPort.generateEmbedding(any())).thenReturn(new float[]{0.1f, 0.2f});
        when(dinoPort.generateEmbedding(any())).thenReturn(new float[]{0.3f, 0.4f});

        service.onImageProtected(new ImageProtectedEvent(1L, userId, image.getGcsPath()));

        assertThat(image.getStatus()).isEqualTo(ImageStatus.INDEXED);
        assertThat(image.getEmbedding()).isNotNull();
        assertThat(image.getEmbeddingDino()).isNotNull();
        assertThat(image.getIndexedAt()).isNotNull();
        verify(imageRepository).save(image);
    }

    @Test
    void onImageProtected_noOpAdapters_skipsEmbedding() {
        when(imageRepository.findById(1L)).thenReturn(Optional.of(image));
        when(storagePort.load(image.getGcsPath())).thenReturn(new byte[]{1, 2, 3});
        when(sscdPort.generateEmbedding(any())).thenReturn(null);
        when(dinoPort.generateEmbedding(any())).thenReturn(null);

        service.onImageProtected(new ImageProtectedEvent(1L, userId, image.getGcsPath()));

        assertThat(image.getStatus()).isEqualTo(ImageStatus.PROTECTED); // 변경 없음
        verify(imageRepository, never()).save(image);
    }

    @Test
    void onImageProtected_imageNotFound_doesNothing() {
        when(imageRepository.findById(99L)).thenReturn(Optional.empty());

        service.onImageProtected(new ImageProtectedEvent(99L, userId, "path"));

        verify(storagePort, never()).load(any());
    }

    @Test
    void onImageProtected_embeddingThrows_marksAsFailed() {
        when(imageRepository.findById(1L)).thenReturn(Optional.of(image));
        when(storagePort.load(any())).thenReturn(new byte[]{1, 2, 3});
        when(sscdPort.generateEmbedding(any())).thenThrow(new RuntimeException("ONNX error"));

        service.onImageProtected(new ImageProtectedEvent(1L, userId, image.getGcsPath()));

        verify(imageRepository, atLeast(1)).findById(1L);
        // 실패 시 FAILED로 변경
        verify(imageRepository, atLeastOnce()).save(argThat(img ->
                img.getStatus() == ImageStatus.FAILED));
    }

    @Test
    void onImageProtected_onlySscdSucceeds_stillIndexed() {
        when(imageRepository.findById(1L)).thenReturn(Optional.of(image));
        when(storagePort.load(image.getGcsPath())).thenReturn(new byte[]{1, 2, 3});
        when(sscdPort.generateEmbedding(any())).thenReturn(new float[]{0.1f});
        when(dinoPort.generateEmbedding(any())).thenReturn(null);

        service.onImageProtected(new ImageProtectedEvent(1L, userId, image.getGcsPath()));

        assertThat(image.getStatus()).isEqualTo(ImageStatus.INDEXED);
        assertThat(image.getEmbedding()).isNotNull();
        assertThat(image.getEmbeddingDino()).isNull();
    }
}
