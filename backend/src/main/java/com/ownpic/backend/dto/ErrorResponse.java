package com.ownpic.backend.dto;

import java.time.Instant;

public record ErrorResponse(
        int status,
        String detail,
        String path,
        Instant timestamp
) {
    public static ErrorResponse of(int status, String detail, String path) {
        return new ErrorResponse(status, detail, path, Instant.now());
    }
}
