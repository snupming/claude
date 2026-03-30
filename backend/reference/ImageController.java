package com.ownpic.image.controller;

import com.ownpic.auth.AuthService;
import com.ownpic.image.ImageDetailResponse;
import com.ownpic.image.ImageService;
import com.ownpic.image.ImageUploadResponse;
import com.ownpic.shared.dto.ApiPaths;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 이미지 REST API.
 *
 * <p>인증 필요: 모든 엔드포인트에 {@code @AuthenticationPrincipal UUID publicId} 사용.
 * JWT subject(publicId, UUID) → 내부 userId(Long)는 {@link AuthService#resolveUserId}로 변환.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/images")
public class ImageController {

    private final ImageService imageService;
    private final AuthService authService;

    public ImageController(ImageService imageService, AuthService authService) {
        this.imageService = imageService;
        this.authService = authService;
    }

    /**
     * 이미지 보호 등록.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse upload(
            @AuthenticationPrincipal UUID publicId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("keywords") String keywords) throws IOException {

        if (keywords == null || keywords.isBlank()) {
            throw new IllegalArgumentException("keywords는 필수 입력입니다.");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일이 비어 있습니다.");
        }

        Long userId = authService.resolveUserId(publicId);
        byte[] imageBytes = file.getBytes();
        return imageService.upload(userId, imageBytes, keywords.strip());
    }

    /**
     * 셀러 이미지 목록 조회 (최신순).
     */
    @GetMapping
    public List<ImageDetailResponse> list(@AuthenticationPrincipal UUID publicId) {
        Long userId = authService.resolveUserId(publicId);
        return imageService.listByUser(userId);
    }

    /**
     * 이미지 상세 조회.
     */
    @GetMapping("/{id}")
    public ImageDetailResponse detail(
            @AuthenticationPrincipal UUID publicId,
            @PathVariable Long id) {
        Long userId = authService.resolveUserId(publicId);
        return imageService.getDetail(userId, id);
    }

    /**
     * 이미지 삭제.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UUID publicId,
            @PathVariable Long id) {
        Long userId = authService.resolveUserId(publicId);
        imageService.delete(userId, id);
    }

    /**
     * 이미지 파일 서빙.
     *
     * <p>LocalFileStorageAdapter(dev) 와 GcsStorageAdapter(prod) 모두
     * {@link com.ownpic.image.port.ImageStoragePort#generateUrl} 에서
     * {@code /api/v1/images/file/{storagePath}} 형식을 반환하고,
     * 이 엔드포인트가 실제 바이트를 스트리밍합니다.
     *
     * <p>소유권 검증: URL의 {pathUserId} 가 JWT 의 userId 와 일치해야 합니다.
     * 불일치 시 403 반환 (이미지 내용 대신 에러 응답).
     *
     * <p>Content-Type: 파일 확장자 기반으로 결정 (png/webp/jpg).
     * Cache-Control: max-age=3600 (CDN 캐싱 가능).
     *
     * @param publicId    JWT subject (UUID)
     * @param pathUserId  URL 경로의 userId (소유권 검증용)
     * @param filename    파일명 (UUID + 확장자)
     */
    @GetMapping("/file/{pathUserId}/{filename}")
    public ResponseEntity<byte[]> serveFile(
            @AuthenticationPrincipal UUID publicId,
            @PathVariable Long pathUserId,
            @PathVariable String filename) {

        Long requesterId = authService.resolveUserId(publicId);

        // 소유권 검증: URL의 userId와 JWT userId 일치 확인
        if (!requesterId.equals(pathUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String storagePath = pathUserId + "/" + filename;
        byte[] imageBytes = imageService.loadImageBytes(storagePath);

        MediaType contentType = resolveContentType(filename);
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(imageBytes);
    }

    private static final Map<String, MediaType> CONTENT_TYPE_MAP = Map.of(
            "png",  MediaType.IMAGE_PNG,
            "jpg",  MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "webp", MediaType.parseMediaType("image/webp")
    );

    private MediaType resolveContentType(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0) {
            String ext = filename.substring(dotIndex + 1).toLowerCase();
            MediaType resolved = CONTENT_TYPE_MAP.get(ext);
            if (resolved != null) return resolved;
        }
        return MediaType.IMAGE_PNG;
    }
}
