package com.ownpic.image.controller;

import com.ownpic.image.ImageService;
import com.ownpic.image.dto.ImageListResponse;
import com.ownpic.image.dto.ImageResponse;
import com.ownpic.shared.dto.ApiPaths;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

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
}
