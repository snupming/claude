package com.ownpic.detection.adapter.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ownpic.google")
public record GoogleScraperProperties(
        int maxDailyRequests,
        long minDelayMs,
        long jitterMs,
        int consecutiveFailureThreshold,
        int requestTimeoutSeconds
) {
    public GoogleScraperProperties {
        if (maxDailyRequests <= 0) maxDailyRequests = 100;
        if (minDelayMs <= 0) minDelayMs = 7000;
        if (jitterMs < 0) jitterMs = 3000;
        if (consecutiveFailureThreshold <= 0) consecutiveFailureThreshold = 3;
        if (requestTimeoutSeconds <= 0) requestTimeoutSeconds = 30;
    }
}
