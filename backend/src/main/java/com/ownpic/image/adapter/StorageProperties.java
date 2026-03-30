package com.ownpic.image.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ownpic.storage")
public record StorageProperties(String basePath) {}
