package com.ratelimiter.algorithm;

import com.ratelimiter.core.RateLimiter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Sliding Window Log Rate Limiter Implementation.
 * 
 * How it works:
 * - Maintains a log (sorted list) of request timestamps per client
 * - On each request, removes expired timestamps outside the window
 * - Counts remaining entries - if below limit, request is allowed
 * - Most accurate algorithm (no approximation)
 * 
 * Trade-off: Higher memory usage O(N) but perfect accuracy.
 * 
 * Time Complexity: O(N) per request (cleanup of expired entries)
 * Space Complexity: O(N) per client where N = max requests in window
 */
public class SlidingWindowLogLimiter implements RateLimiter {

    private final long maxRequests;
    private final long windowSizeMs;
    private final ConcurrentHashMap<String, Deque<Long>> requestLogs;

    public SlidingWindowLogLimiter(long maxRequests, long windowSizeSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeSeconds * 1000;
        this.requestLogs = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();
        Deque<Long> log = requestLogs.computeIfAbsent(clientId, k -> new ConcurrentLinkedDeque<>());

        // Remove expired entries
        long windowStart = now - windowSizeMs;
        while (!log.isEmpty() && log.peekFirst() <= windowStart) {
            log.pollFirst();
        }

        // Check if under limit
        if (log.size() < maxRequests) {
            log.addLast(now);
            return true;
        }
        return false;
    }

    @Override
    public long getRemainingRequests(String clientId) {
        Deque<Long> log = requestLogs.get(clientId);
        if (log == null) return maxRequests;

        long now = System.currentTimeMillis();
        long windowStart = now - windowSizeMs;

        long activeRequests = log.stream().filter(t -> t > windowStart).count();
        return Math.max(0, maxRequests - activeRequests);
    }

    @Override
    public void reset(String clientId) {
        requestLogs.remove(clientId);
    }
}
