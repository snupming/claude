package com.ownpic.detection.adapter.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Profile("google")
public class ScraperRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ScraperRateLimiter.class);

    private final Semaphore semaphore = new Semaphore(1);
    private final AtomicInteger dailyCount = new AtomicInteger(0);
    private final AtomicReference<LocalDate> currentDay = new AtomicReference<>(LocalDate.now());
    private final GoogleScraperProperties props;

    public ScraperRateLimiter(GoogleScraperProperties props) {
        this.props = props;
    }

    public boolean tryAcquire() {
        resetIfNewDay();
        if (dailyCount.get() >= props.maxDailyRequests()) {
            log.warn("Google scraper daily limit reached: {}/{}", dailyCount.get(), props.maxDailyRequests());
            return false;
        }
        try {
            semaphore.acquire();
            dailyCount.incrementAndGet();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release() {
        semaphore.release();
    }

    public void waitDelay() {
        long delay = props.minDelayMs() + ThreadLocalRandom.current().nextLong(props.jitterMs() + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void resetIfNewDay() {
        LocalDate today = LocalDate.now();
        LocalDate stored = currentDay.get();
        if (!today.equals(stored) && currentDay.compareAndSet(stored, today)) {
            dailyCount.set(0);
            log.info("Google scraper daily counter reset");
        }
    }
}
