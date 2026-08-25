package com.ratelimiter.algorithm;

import com.ratelimiter.core.RateLimiter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding Window Counter Rate Limiter Implementation.
 * 
 * How it works:
 * - Maintains counters for current and previous window
 * - Calculates weighted count: previous_count * overlap_percentage + current_count
 * - Smooths the boundary spike problem of Fixed Window
 * 
 * Used by: Cloudflare, Kong API Gateway
 * 
 * Time Complexity: O(1) per request
 * Space Complexity: O(1) per client
 */
public class SlidingWindowCounterLimiter implements RateLimiter {

    private final long maxRequests;
    private final long windowSizeMs;
    private final ConcurrentHashMap<String, WindowState> windows;

    public SlidingWindowCounterLimiter(long maxRequests, long windowSizeSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeSeconds * 1000;
        this.windows = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized boolean allowRequest(String clientId) {
        WindowState state = windows.computeIfAbsent(clientId, k -> new WindowState());
        long now = System.currentTimeMillis();
        long currentWindow = now / windowSizeMs;

        if (currentWindow != state.currentWindowStart) {
            state.previousCount.set(state.currentCount.get());
            state.currentCount.set(0);
            state.currentWindowStart = currentWindow;
        }

        long elapsedInCurrentWindow = now % windowSizeMs;
        double previousWindowWeight = 1.0 - ((double) elapsedInCurrentWindow / windowSizeMs);
        long weightedCount = (long) (state.previousCount.get() * previousWindowWeight)
                + state.currentCount.get();

        if (weightedCount < maxRequests) {
            state.currentCount.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public long getRemainingRequests(String clientId) {
        WindowState state = windows.get(clientId);
        if (state == null) return maxRequests;
        long now = System.currentTimeMillis();
        long elapsedInCurrentWindow = now % windowSizeMs;
        double previousWindowWeight = 1.0 - ((double) elapsedInCurrentWindow / windowSizeMs);
        long weightedCount = (long) (state.previousCount.get() * previousWindowWeight)
                + state.currentCount.get();
        return Math.max(0, maxRequests - weightedCount);
    }

    @Override
    public void reset(String clientId) {
        windows.remove(clientId);
    }

    private static class WindowState {
        volatile long currentWindowStart;
        final AtomicLong currentCount;
        final AtomicLong previousCount;

        WindowState() {
            this.currentWindowStart = System.currentTimeMillis();
            this.currentCount = new AtomicLong(0);
            this.previousCount = new AtomicLong(0);
        }
    }
}
