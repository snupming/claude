package com.ownpic.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ownpic.cors")
public record CorsProperties(
        String allowedOrigins
) {}
