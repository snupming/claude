package com.ownpic.image.dto;

import jakarta.validation.constraints.NotBlank;

public record ConnectPlatformRequest(
        @NotBlank(message = "플랫폼은 필수입니다")
        String platform
) {}
