package com.ownpic.auth.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ownpic.naver-commerce")
public record NaverCommerceProperties(
        String publicKey
) {}
