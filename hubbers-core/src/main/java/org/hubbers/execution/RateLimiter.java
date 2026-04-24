package org.hubbers.execution;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple sliding-window rate limiter per provider.
 *
 * <p>Tracks the number of requests made to each model provider within a configurable
 * time window and rejects requests that exceed the maximum.</p>
 *
 * @since 0.1.0
 */
@Slf4j
public class RateLimiter {

    private final int maxRequestsPerWindow;
    private final long windowMillis;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    /**
     * Create a rate limiter.
     *
     * @param maxRequestsPerWindow maximum requests allowed per window
     * @param windowMillis window duration in milliseconds
     */
    public RateLimiter(int maxRequestsPerWindow, long windowMillis) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMillis = windowMillis;
    }

    /** Default rate limiter: 60 requests per minute. */
    public static RateLimiter defaultLimiter() {
        return new RateLimiter(60, 60_000L);
    }

    /**
     * Attempt to acquire a permit for the given provider.
     *
     * @param providerName the model provider name
     * @return true if the request is allowed, false if rate-limited
     */
    public boolean tryAcquire(String providerName) {
        var counter = counters.computeIfAbsent(providerName, k -> new WindowCounter());
        long now = Instant.now().toEpochMilli();

        synchronized (counter) {
            if (now - counter.windowStart > windowMillis) {
                // Reset window
                counter.windowStart = now;
                counter.count.set(0);
            }
            if (counter.count.get() >= maxRequestsPerWindow) {
                log.warn("Rate limit exceeded for provider '{}': {}/{} in window",
                        providerName, counter.count.get(), maxRequestsPerWindow);
                return false;
            }
            counter.count.incrementAndGet();
            return true;
        }
    }

    /**
     * Get the current request count for a provider in the active window.
     *
     * @param providerName the model provider name
     * @return current count, or 0 if no requests have been made
     */
    public int getCurrentCount(String providerName) {
        var counter = counters.get(providerName);
        return counter != null ? counter.count.get() : 0;
    }

    private static class WindowCounter {
        long windowStart = Instant.now().toEpochMilli();
        final AtomicInteger count = new AtomicInteger(0);
    }
}
