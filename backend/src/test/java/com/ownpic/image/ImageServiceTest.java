package com.ownpic.image;

import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.dto.ImageResponse;
import com.ownpic.image.port.ImageStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock ImageRepository imageRepository;
    @Mock UserRepository userRepository;
    @Mock ImageStoragePort storagePort;
    @Mock ImageIngestionService ingestionService;

    @InjectMocks ImageService imageService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        setField(user, "id", userId);
        user.setName("테스트");
        user.setEmail("test@example.com");
        user.setImagesUsed(5);
    }

    // --- upload ---

    @Test
    void upload_success_returnsResponse() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(file.getOriginalFilename()).thenReturn("test.jpg");

        Image image = createImage(1L, userId, "test.jpg", ImageStatus.PENDING);
        when(ingestionService.ingest(eq(userId), any(byte[].class), isNull(), eq("test.jpg"))).thenReturn(image);

        ImageResponse response = imageService.upload(userId, file, null);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("test.jpg");
    }

    @Test
    void upload_emptyFile_throwsBadRequest() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> imageService.upload(userId, file, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void upload_nullFile_throwsBadRequest() {
        assertThatThrownBy(() -> imageService.upload(userId, null, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- getImages ---

    @Test
    void getImages_noFilter_returnsPagedList() {
        Image img = createImage(1L, userId, "test.jpg", ImageStatus.INDEXED);
        var page = new PageImpl<>(List.of(img), PageRequest.of(0, 20), 1);
        when(imageRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20)))
                .thenReturn(page);

        var result = imageService.getImages(userId, 0, 20, null, null);

        assertThat(result.images()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getImages_statusFilter_usesStatusQuery() {
        Image img = createImage(1L, userId, "test.jpg", ImageStatus.PROTECTED);
        var page = new PageImpl<>(List.of(img), PageRequest.of(0, 20), 1);
        when(imageRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ImageStatus.PROTECTED, PageRequest.of(0, 20)))
                .thenReturn(page);

        var result = imageService.getImages(userId, 0, 20, "PROTECTED", null);

        assertThat(result.images()).hasSize(1);
    }

    // --- getImage ---

    @Test
    void getImage_found_returnsResponse() {
        Image img = createImage(1L, userId, "test.jpg", ImageStatus.INDEXED);
        when(imageRepository.findById(1L)).thenReturn(Optional.of(img));

        var response = imageService.getImage(userId, 1L);

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getImage_notFound_throws404() {
        when(imageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.getImage(userId, 999L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getImage_wrongUser_throws404() {
        UUID otherUserId = UUID.randomUUID();
        Image img = createImage(1L, otherUserId, "test.jpg", ImageStatus.INDEXED);
        when(imageRepository.findById(1L)).thenReturn(Optional.of(img));

        assertThatThrownBy(() -> imageService.getImage(userId, 1L))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- deleteImage ---

    @Test
    void deleteImage_success_deletesAndDecrementsQuota() {
        Image img = createImage(1L, userId, "test.jpg", ImageStatus.INDEXED);
        img.setGcsPath("user123/test.jpg");
        when(imageRepository.findById(1L)).thenReturn(Optional.of(img));

        imageService.deleteImage(userId, 1L);

        verify(storagePort).deleteQuietly("user123/test.jpg");
        verify(imageRepository).delete(img);
        assertThat(user.getImagesUsed()).isEqualTo(4);
        verify(userRepository).save(user);
    }

    @Test
    void deleteImage_noGcsPath_skipsStorageDelete() {
        Image img = createImage(1L, userId, "test.jpg", ImageStatus.PENDING);
        // gcsPath is null
        when(imageRepository.findById(1L)).thenReturn(Optional.of(img));

        imageService.deleteImage(userId, 1L);

        verify(storagePort, never()).deleteQuietly(anyString());
        verify(imageRepository).delete(img);
    }

    @Test
    void deleteImage_notFound_throws404() {
        when(imageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.deleteImage(userId, 999L))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- loadImageBytes ---

    @Test
    void loadImageBytes_delegatesToStoragePort() {
        byte[] expected = new byte[]{1, 2, 3};
        when(storagePort.load("some/path.jpg")).thenReturn(expected);

        byte[] result = imageService.loadImageBytes("some/path.jpg");

        assertThat(result).isEqualTo(expected);
    }

    // --- Helpers ---

    private Image createImage(Long id, UUID ownerId, String name, ImageStatus status) {
        Image img = new Image();
        setField(img, "id", id);
        img.setName(name);
        img.setStatus(status);
        img.setFileSize(1024);
        img.setWidth(100);
        img.setHeight(100);

        User owner;
        if (ownerId.equals(userId)) {
            owner = user;
        } else {
            owner = new User();
            setField(owner, "id", ownerId);
        }
        img.setUser(owner);
        return img;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var clazz = target.getClass();
            while (clazz != null) {
                try {
                    var field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
