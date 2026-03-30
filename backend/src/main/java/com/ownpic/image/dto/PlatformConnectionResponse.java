package com.ownpic.image.dto;

import java.time.Instant;

public record PlatformConnectionResponse(
        Long id,
        String platform,
        String storeName,
        String status,
        Instant lastSyncedAt,
        Instant createdAt
) {}
