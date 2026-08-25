package com.ratelimiter.algorithm;

import com.ratelimiter.core.RateLimiter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket Rate Limiter Implementation.
 * 
 * How it works:
 * - A bucket holds tokens up to a maximum capacity
 * - Tokens are added at a fixed refill rate
 * - Each request consumes one token
 * - If no tokens available, request is rejected
 * - Allows controlled bursts (up to bucket capacity)
 * 
 * Used by: AWS, Stripe, Google Cloud
 * 
 * Time Complexity: O(1) per request
 * Space Complexity: O(1) per client
 */
public class TokenBucketLimiter implements RateLimiter {

    private final long capacity;
    private final long refillRatePerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets;

    public TokenBucketLimiter(long capacity, long refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.buckets = new ConcurrentHashMap<>();
    }

    @Override
    public boolean allowRequest(String clientId) {
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(capacity));
        return bucket.tryConsume();
    }

    @Override
    public long getRemainingRequests(String clientId) {
        Bucket bucket = buckets.get(clientId);
        if (bucket == null) return capacity;
        bucket.refill();
        return bucket.tokens.get();
    }

    @Override
    public void reset(String clientId) {
        buckets.remove(clientId);
    }

    /**
     * Internal bucket state per client.
     * Uses atomic operations for thread safety without explicit locking.
     */
    private class Bucket {
        private final AtomicLong tokens;
        private volatile long lastRefillTimestamp;

        Bucket(long initialTokens) {
            this.tokens = new AtomicLong(initialTokens);
            this.lastRefillTimestamp = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillTimestamp;
            long tokensToAdd = (elapsed / 1_000_000_000L) * refillRatePerSecond;

            if (tokensToAdd > 0) {
                long newTokens = Math.min(capacity, tokens.get() + tokensToAdd);
                tokens.set(newTokens);
                lastRefillTimestamp = now;
            }
        }
    }
}
