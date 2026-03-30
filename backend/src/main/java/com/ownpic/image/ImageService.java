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
import com.ownpic.image.port.WatermarkPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_DIMENSION = 4096;

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final ImageStoragePort storagePort;
    private final WatermarkPort watermarkPort;
    private final ApplicationEventPublisher eventPublisher;

    public ImageService(ImageRepository imageRepository,
                        UserRepository userRepository,
                        ImageStoragePort storagePort,
                        WatermarkPort watermarkPort,
                        ApplicationEventPublisher eventPublisher) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.storagePort = storagePort;
        this.watermarkPort = watermarkPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ImageResponse upload(UUID userId, MultipartFile file, String keywords) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 필요합니다.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다. (JPEG, PNG, WebP만 가능)");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 10MB 이하여야 합니다.");
        }

        try {
            byte[] fileBytes = file.getBytes();

            // 이미지 메타 추출
            var bufferedImage = ImageIO.read(new ByteArrayInputStream(fileBytes));
            if (bufferedImage == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 이미지 파일입니다.");
            }
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();

            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "이미지 해상도는 %dx%d 이하여야 합니다.".formatted(MAX_DIMENSION, MAX_DIMENSION));
            }

            // SHA256 해시 → 중복 체크
            String sha256 = computeSha256(fileBytes);
            if (imageRepository.existsByUserIdAndSha256(userId, sha256)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 이미지입니다.");
            }

            // 쿼터 체크
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
            if (user.getImagesUsed() >= user.getImageQuota()) {
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "이미지 등록 한도를 초과했습니다.");
            }

            // 워터마킹
            String payload = "ownpic:" + userId + ":" + sha256.substring(0, 8);
            var watermarkResult = watermarkPort.encode(fileBytes, payload);

            // DB INSERT (먼저 저장하여 ID 확보)
            var image = new Image();
            image.setUser(user);
            image.setName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "image");
            image.setSha256(sha256);
            image.setFileSize((int) file.getSize());
            image.setWidth(width);
            image.setHeight(height);
            image.setStatus(ImageStatus.PROTECTED);
            image.setWatermarkPayload(watermarkResult.payload());
            image.setKeywords(keywords);
            image.setSourceType(SourceType.UPLOAD);
            imageRepository.save(image);

            // 파일 저장
            String extension = extractExtension(file.getContentType());
            String storagePath = storagePort.store(userId, image.getId(), watermarkResult.watermarkedImage(), extension);
            image.setGcsPath(storagePath);

            // 사용량 증가
            user.setImagesUsed(user.getImagesUsed() + 1);
            userRepository.save(user);

            // 비동기 임베딩 트리거
            eventPublisher.publishEvent(new ImageProtectedEvent(image.getId()));

            return toResponse(image);
        } catch (ResponseStatusException e) {
            throw e;
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

    private String computeSha256(byte[] data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String extractExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
