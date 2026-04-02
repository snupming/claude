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
@Profile("google-legacy")
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
        this.strategies = List.of(lensUpload, searchByImage);
        this.rateLimiter = rateLimiter;
        this.failureThreshold = props.consecutiveFailureThreshold();
        log.info("Google reverse image search ENABLED — strategies: {}, failureThreshold: {}",
                strategies.stream().map(GoogleReverseImageSearchStrategy::name).toList(), failureThreshold);
    }

    @Override
    public List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults) {
        log.info("[Google] searchByImage 시작 — imageBytes={}KB, maxResults={}", imageBytes.length / 1024, maxResults);

        if (!rateLimiter.tryAcquire()) {
            log.warn("[Google] Rate limit 초과 — 요청 거부됨");
            return List.of();
        }

        try {
            int strategyIdx = activeStrategyIndex.get();
            GoogleReverseImageSearchStrategy strategy = strategies.get(strategyIdx);
            log.info("[Google] 전략 선택: {} (index={})", strategy.name(), strategyIdx);

            try {
                List<ReverseSearchResult> results = strategy.search(imageBytes, maxResults);
                consecutiveFailures.set(0);
                log.info("[Google] {} 완료 — {} 결과", strategy.name(), results.size());
                return results;

            } catch (GoogleSearchException e) {
                if (e.isCaptcha()) {
                    int failures = consecutiveFailures.incrementAndGet();
                    log.warn("[Google] {} CAPTCHA 감지 ({}/{}) — status={}",
                            strategy.name(), failures, failureThreshold, e.getStatusCode());

                    if (failures >= failureThreshold) {
                        switchStrategy(strategyIdx);
                        return retryWithFallback(imageBytes, maxResults);
                    }
                } else {
                    log.warn("[Google] {} 에러 — status={}, message={}",
                            strategy.name(), e.getStatusCode(), e.getMessage());
                }
                return List.of();
            }
        } catch (Exception e) {
            log.error("[Google] 예상치 못한 에러", e);
            return List.of();
        } finally {
            rateLimiter.release();
            rateLimiter.waitDelay();
        }
    }

    private List<ReverseSearchResult> retryWithFallback(byte[] imageBytes, int maxResults) {
        GoogleReverseImageSearchStrategy fallback = strategies.get(activeStrategyIndex.get());
        log.info("[Google] Fallback 전략: {}", fallback.name());
        try {
            List<ReverseSearchResult> results = fallback.search(imageBytes, maxResults);
            consecutiveFailures.set(0);
            log.info("[Google] Fallback {} 성공 — {} 결과", fallback.name(), results.size());
            return results;
        } catch (Exception e) {
            log.error("[Google] Fallback {} 실패: {}", fallback.name(), e.getMessage());
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
