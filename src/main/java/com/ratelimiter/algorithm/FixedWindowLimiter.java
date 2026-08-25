package com.ratelimiter.algorithm;

import com.ratelimiter.core.RateLimiter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed Window Counter Rate Limiter Implementation.
 * 
 * How it works:
 * - Divides time into fixed-size windows (e.g., 60-second intervals)
 * - Maintains a counter per window per client
 * - Counter resets at the start of each new window
 * - Simplest algorithm but has boundary spike issue
 * 
 * Known issue: A burst at window boundary can allow 2x the limit
 * (e.g., 100 requests at 0:59 + 100 at 1:00 = 200 in 1 second)
 * 
 * Use case: Simple rate limiting where boundary precision is not critical.
 * 
 * Time Complexity: O(1) per request
 * Space Complexity: O(1) per client
 */
public class FixedWindowLimiter implements RateLimiter {

    private final long maxRequests;
    private final long windowSizeMs;
    private final ConcurrentHashMap<String, WindowCounter> counters;

    public FixedWindowLimiter(long maxRequests, long windowSizeSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeSeconds * 1000;
        this.counters = new ConcurrentHashMap<>();
    }

    @Override
    public boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();
        long currentWindow = now / windowSizeMs;

        WindowCounter counter = counters.computeIfAbsent(clientId, k -> new WindowCounter());

        synchronized (counter) {
            // Reset if we moved to a new window
            if (counter.windowId != currentWindow) {
                counter.windowId = currentWindow;
                counter.count.set(0);
            }

            if (counter.count.get() < maxRequests) {
                counter.count.incrementAndGet();
                return true;
            }
            return false;
        }
    }

    @Override
    public long getRemainingRequests(String clientId) {
        WindowCounter counter = counters.get(clientId);
        if (counter == null) return maxRequests;

        long currentWindow = System.currentTimeMillis() / windowSizeMs;
        if (counter.windowId != currentWindow) return maxRequests;

        return Math.max(0, maxRequests - counter.count.get());
    }

    @Override
    public void reset(String clientId) {
        counters.remove(clientId);
    }

    private static class WindowCounter {
        volatile long windowId;
        final AtomicLong count;

        WindowCounter() {
            this.windowId = 0;
            this.count = new AtomicLong(0);
        }
    }
}
