package com.ownpic.image.dto;

import java.time.Instant;

public record ImageResponse(
        Long id,
        String name,
        String storagePath,
        String status,
        int fileSize,
        int width,
        int height,
        String sourceType,
        String sourcePlatform,
        String sourceProductId,
        String sourceImageUrl,
        Instant createdAt,
        Instant indexedAt
) {}
