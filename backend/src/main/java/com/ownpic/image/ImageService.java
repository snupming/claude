package com.ownpic.image;

import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.domain.SourceType;
import com.ownpic.image.dto.ImageListResponse;
import com.ownpic.image.dto.ImageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ImageService {

    private final ImageRepository imageRepository;

    public ImageService(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
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

    private ImageResponse toResponse(Image image) {
        return new ImageResponse(
                image.getId(),
                image.getName(),
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
