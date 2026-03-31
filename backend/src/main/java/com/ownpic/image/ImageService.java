package com.ownpic.image;

import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.domain.SourceType;
import com.ownpic.image.dto.ImageListResponse;
import com.ownpic.image.dto.ImageResponse;
import com.ownpic.image.port.ImageStoragePort;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@Service
public class ImageService {

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final ImageStoragePort storagePort;
    private final ImageIngestionService ingestionService;

    public ImageService(ImageRepository imageRepository,
                        UserRepository userRepository,
                        ImageStoragePort storagePort,
                        ImageIngestionService ingestionService) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.storagePort = storagePort;
        this.ingestionService = ingestionService;
    }

    public ImageResponse upload(UUID userId, MultipartFile file, String keywords) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 필요합니다.");
        }

        try {
            byte[] imageBytes = file.getBytes();
            Image image = ingestionService.ingest(userId, imageBytes, keywords, file.getOriginalFilename());
            return toResponse(image);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public ImageListResponse getImages(UUID userId, int page, int size, String status, String sourceType) {
        var pageable = PageRequest.of(page, size);

        var result = (status != null)
                ? imageRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ImageStatus.valueOf(status), pageable)
                : (sourceType != null)
                ? imageRepository.findByUserIdAndSourceTypeOrderByCreatedAtDesc(userId, SourceType.valueOf(sourceType), pageable)
                : imageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        var images = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new ImageListResponse(images, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ImageResponse getImage(UUID userId, Long imageId) {
        var image = imageRepository.findById(imageId)
                .filter(img -> img.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."));
        return toResponse(image);
    }

    @Transactional
    public void deleteImage(UUID userId, Long imageId) {
        var image = imageRepository.findById(imageId)
                .filter(img -> img.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."));

        if (image.getGcsPath() != null) {
            storagePort.deleteQuietly(image.getGcsPath());
        }
        imageRepository.delete(image);

        User user = image.getUser();
        if (user.getImagesUsed() > 0) {
            user.setImagesUsed(user.getImagesUsed() - 1);
            userRepository.save(user);
        }
    }

    public byte[] loadImageBytes(String storagePath) {
        return storagePort.load(storagePath);
    }

    private ImageResponse toResponse(Image image) {
        return new ImageResponse(
                image.getId(),
                image.getName(),
                image.getGcsPath(),
                image.getStatus().name(),
                image.getFileSize(),
                image.getWidth(),
                image.getHeight(),
                image.getSourceType().name(),
                image.getSourcePlatform(),
                image.getSourceProductId(),
                image.getSourceImageUrl(),
                image.getCreatedAt(),
                image.getIndexedAt()
        );
    }
}
