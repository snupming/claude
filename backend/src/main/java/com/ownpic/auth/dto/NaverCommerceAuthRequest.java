package com.ownpic.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record NaverCommerceAuthRequest(
        @NotBlank String token
) {}
