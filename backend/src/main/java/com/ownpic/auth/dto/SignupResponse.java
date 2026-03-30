package com.ownpic.auth.dto;

public record SignupResponse(
        String id,
        String name,
        String email,
        String role
) {}
