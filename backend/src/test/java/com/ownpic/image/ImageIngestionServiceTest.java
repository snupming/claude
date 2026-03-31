package com.ownpic.image;

import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.exception.ImageDuplicateException;
import com.ownpic.image.exception.ImageValidationException;
import com.ownpic.image.port.ImageStoragePort;
import com.ownpic.image.port.WatermarkPort;
import com.ownpic.image.port.WatermarkPort.WatermarkResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageIngestionServiceTest {

    @Mock ImageRepository imageRepository;
    @Mock UserRepository userRepository;
    @Mock WatermarkPort watermarkPort;
    @Mock ImageStoragePort storagePort;
    @Mock ApplicationEventPublisher eventPublisher;

    ImageIngestionService service;
    UUID userId;
    User user;

    @BeforeEach
    void setUp() {
        // TransactionTemplate을 mock 대신 직접 실행하도록 설정
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });

        service = new ImageIngestionService(
                imageRepository, userRepository, watermarkPort,
                storagePort, eventPublisher, txTemplate);

        userId = UUID.randomUUID();
        user = new User();
        user.setName("테스트");
        user.setEmail("test@test.com");
        user.setPassword("encoded");
        user.setImageQuota(50);
        user.setImagesUsed(0);
    }

    private byte[] createTestPng(int width, int height) throws Exception {
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void ingest_validImage_savesAndReturnsImage() throws Exception {
        byte[] imageBytes = createTestPng(200, 200);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(imageRepository.findByUserIdAndSha256(eq(userId), any())).thenReturn(Optional.empty());
        when(imageRepository.allocateId()).thenReturn(1L);
        when(watermarkPort.encode(any(), any())).thenReturn(
                new WatermarkResult(imageBytes, "payload", 200, 200));
        when(storagePort.save(eq(userId), any())).thenReturn(userId + "/test.png");
        when(imageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Image result = service.ingest(userId, imageBytes, "키워드", "test.png");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ImageStatus.PROTECTED);
        assertThat(result.getKeywords()).isEqualTo("키워드");
        verify(eventPublisher).publishEvent(any(ImageProtectedEvent.class));
    }

    @Test
    void ingest_nullImage_throwsValidation() {
        assertThatThrownBy(() -> service.ingest(userId, null, null, "test.png"))
                .isInstanceOf(ImageValidationException.class);
    }

    @Test
    void ingest_emptyImage_throwsValidation() {
        assertThatThrownBy(() -> service.ingest(userId, new byte[0], null, "test.png"))
                .isInstanceOf(ImageValidationException.class);
    }

    @Test
    void ingest_oversizedImage_throwsValidation() {
        byte[] large = new byte[11 * 1024 * 1024]; // 11MB
        assertThatThrownBy(() -> service.ingest(userId, large, null, "test.png"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void ingest_quotaExceeded_throwsPaymentRequired() throws Exception {
        byte[] imageBytes = createTestPng(200, 200);
        user.setImagesUsed(50);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.ingest(userId, imageBytes, null, "test.png"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("한도");
    }

    @Test
    void ingest_duplicateSha256_throwsDuplicate() throws Exception {
        byte[] imageBytes = createTestPng(200, 200);
        Image existing = new Image();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(imageRepository.findByUserIdAndSha256(eq(userId), any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.ingest(userId, imageBytes, null, "test.png"))
                .isInstanceOf(ImageDuplicateException.class);
    }

    @Test
    void ingest_userNotFound_throwsNotFound() throws Exception {
        byte[] imageBytes = createTestPng(200, 200);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingest(userId, imageBytes, null, "test.png"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("사용자");
    }

    @Test
    void ingest_storageFails_cleansUp() throws Exception {
        byte[] imageBytes = createTestPng(200, 200);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(imageRepository.findByUserIdAndSha256(eq(userId), any())).thenReturn(Optional.empty());
        when(imageRepository.allocateId()).thenReturn(1L);
        when(watermarkPort.encode(any(), any())).thenReturn(
                new WatermarkResult(imageBytes, "payload", 200, 200));
        when(storagePort.save(eq(userId), any())).thenReturn("path");
        // TX#2에서 예외 발생 시뮬레이션을 위해 imageRepository.save에서 예외
        when(imageRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> service.ingest(userId, imageBytes, null, "test.png"))
                .isInstanceOf(RuntimeException.class);

        verify(storagePort).deleteQuietly("path");
    }
}
