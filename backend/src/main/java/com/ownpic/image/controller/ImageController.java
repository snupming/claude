package com.ownpic.image.controller;

import com.ownpic.image.ImageService;
import com.ownpic.image.dto.ImageListResponse;
import com.ownpic.image.dto.ImageResponse;
import com.ownpic.shared.dto.ApiPaths;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(ApiPaths.V1 + "/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping
    public ResponseEntity<ImageListResponse> getImages(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType
    ) {
        return ResponseEntity.ok(imageService.getImages(userId, page, size, status, sourceType));
    }

    @PostMapping("/upload")
    public ResponseEntity<ImageResponse> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "keywords", required = false) String keywords
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageService.upload(userId, file, keywords));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImageResponse> getImage(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(imageService.getImage(userId, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long id
    ) {
        imageService.deleteImage(userId, id);
    }

    @GetMapping("/file/{pathUserId}/{filename}")
    public ResponseEntity<byte[]> serveFile(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID pathUserId,
            @PathVariable String filename
    ) {
        if (!userId.equals(pathUserId)) {
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
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
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
