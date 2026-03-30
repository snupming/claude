package com.ownpic.image.dto;

import java.util.List;

public record ImageListResponse(
        List<ImageResponse> images,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
