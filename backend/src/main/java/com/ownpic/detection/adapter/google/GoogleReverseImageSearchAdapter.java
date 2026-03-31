package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("google")
@EnableConfigurationProperties(GoogleScraperProperties.class)
public class GoogleReverseImageSearchAdapter implements ReverseImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(GoogleReverseImageSearchAdapter.class);

    private final List<GoogleReverseImageSearchStrategy> strategies;
    private final ScraperRateLimiter rateLimiter;
    private final AtomicInteger activeStrategyIndex = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final int failureThreshold;

    public GoogleReverseImageSearchAdapter(GoogleSearchByImageStrategy searchByImage,
                                           GoogleLensUploadStrategy lensUpload,
                                           ScraperRateLimiter rateLimiter,
                                           GoogleScraperProperties props) {
        this.strategies = List.of(searchByImage, lensUpload);
        this.rateLimiter = rateLimiter;
        this.failureThreshold = props.consecutiveFailureThreshold();
    }

    @Override
    public List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults) {
        if (!rateLimiter.tryAcquire()) {
            return List.of();
        }

        try {
            int strategyIdx = activeStrategyIndex.get();
            GoogleReverseImageSearchStrategy strategy = strategies.get(strategyIdx);

            try {
                List<ReverseSearchResult> results = strategy.search(imageBytes, maxResults);
                consecutiveFailures.set(0);
                return results;

            } catch (GoogleSearchException e) {
                if (e.isCaptcha()) {
                    int failures = consecutiveFailures.incrementAndGet();
                    log.warn("{} CAPTCHA detected ({}/{})", strategy.name(), failures, failureThreshold);

                    if (failures >= failureThreshold) {
                        switchStrategy(strategyIdx);
                        return retryWithFallback(imageBytes, maxResults);
                    }
                } else {
                    log.warn("{} error: {}", strategy.name(), e.getMessage());
                }
                return List.of();
            }
        } catch (Exception e) {
            log.error("Unexpected error in Google reverse image search", e);
            return List.of();
        } finally {
            rateLimiter.release();
            rateLimiter.waitDelay();
        }
    }

    private List<ReverseSearchResult> retryWithFallback(byte[] imageBytes, int maxResults) {
        GoogleReverseImageSearchStrategy fallback = strategies.get(activeStrategyIndex.get());
        try {
            List<ReverseSearchResult> results = fallback.search(imageBytes, maxResults);
            consecutiveFailures.set(0);
            log.info("Fallback to {} succeeded", fallback.name());
            return results;
        } catch (Exception e) {
            log.error("Fallback strategy {} also failed: {}", fallback.name(), e.getMessage());
            return List.of();
        }
    }

    private void switchStrategy(int expectedIndex) {
        int next = (expectedIndex + 1) % strategies.size();
        if (activeStrategyIndex.compareAndSet(expectedIndex, next)) {
            consecutiveFailures.set(0);
            log.warn("Switched Google strategy: {} → {}",
                    strategies.get(expectedIndex).name(),
                    strategies.get(next).name());
        }
    }
}
