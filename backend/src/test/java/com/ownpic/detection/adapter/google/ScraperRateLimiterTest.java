package com.ownpic.detection.adapter.google;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperRateLimiterTest {

    private GoogleScraperProperties testProps(int maxDaily, long minDelay, long jitter) {
        return new GoogleScraperProperties(maxDaily, minDelay, jitter, 3, 30);
    }

    @Test
    void tryAcquire_withinLimit_returnsTrue() {
        var limiter = new ScraperRateLimiter(testProps(100, 0, 0));

        assertThat(limiter.tryAcquire()).isTrue();
        limiter.release();
    }

    @Test
    void tryAcquire_exceedsLimit_returnsFalse() {
        var limiter = new ScraperRateLimiter(testProps(2, 0, 0));

        assertThat(limiter.tryAcquire()).isTrue();
        limiter.release();
        assertThat(limiter.tryAcquire()).isTrue();
        limiter.release();
        // 3번째 시도 → 한도 초과
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void tryAcquire_serialAccess_works() {
        var limiter = new ScraperRateLimiter(testProps(10, 0, 0));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
            limiter.release();
        }
    }

    @Test
    void waitDelay_zeroDelay_returnsImmediately() {
        var limiter = new ScraperRateLimiter(testProps(100, 0, 0));
        long start = System.currentTimeMillis();
        limiter.waitDelay();
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isLessThan(500);
    }

    @Test
    void waitDelay_withDelay_waits() {
        var limiter = new ScraperRateLimiter(testProps(100, 200, 0));
        long start = System.currentTimeMillis();
        limiter.waitDelay();
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isGreaterThanOrEqualTo(180); // 약간의 타이밍 여유
    }
}
