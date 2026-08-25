package com.ratelimiter.algorithm;

import com.ratelimiter.core.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for all rate limiter implementations.
 * Tests correctness, boundary conditions, and thread safety.
 */
class RateLimiterTest {

    @Test
    void tokenBucket_allowsRequestsWithinCapacity() {
        RateLimiter limiter = new TokenBucketLimiter(5, 1);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allowRequest("client1"));
        }
        // 6th request should be rejected (bucket empty)
        assertFalse(limiter.allowRequest("client1"));
    }

    @Test
    void tokenBucket_differentClientsHaveSeparateBuckets() {
        RateLimiter limiter = new TokenBucketLimiter(3, 1);

        assertTrue(limiter.allowRequest("clientA"));
        assertTrue(limiter.allowRequest("clientA"));
        assertTrue(limiter.allowRequest("clientA"));
        assertFalse(limiter.allowRequest("clientA"));

        // clientB should still have full capacity
        assertTrue(limiter.allowRequest("clientB"));
    }

    @Test
    void fixedWindow_resetsAfterWindowExpires() throws InterruptedException {
        RateLimiter limiter = new FixedWindowLimiter(3, 1); // 3 requests per second

        assertTrue(limiter.allowRequest("client1"));
        assertTrue(limiter.allowRequest("client1"));
        assertTrue(limiter.allowRequest("client1"));
        assertFalse(limiter.allowRequest("client1"));

        // Wait for window to reset
        Thread.sleep(1100);
        assertTrue(limiter.allowRequest("client1"));
    }

    @Test
    void slidingWindowLog_exactTracking() {
        RateLimiter limiter = new SlidingWindowLogLimiter(5, 60);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allowRequest("client1"));
        }
        assertFalse(limiter.allowRequest("client1"));
        assertEquals(0, limiter.getRemainingRequests("client1"));
    }

    @Test
    void slidingWindowCounter_smoothsBoundary() {
        RateLimiter limiter = new SlidingWindowCounterLimiter(10, 60);

        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.allowRequest("client1"));
        }
        assertFalse(limiter.allowRequest("client1"));
    }

    @Test
    void concurrency_threadSafeUnderLoad() throws InterruptedException {
        RateLimiter limiter = new TokenBucketLimiter(100, 0); // 100 tokens, no refill
        int threadCount = 200;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    if (limiter.allowRequest("concurrent-client")) {
                        allowed.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Exactly 100 should be allowed (bucket capacity)
        assertEquals(100, allowed.get(), "Exactly capacity requests should be allowed");
        assertEquals(100, rejected.get(), "Remaining should be rejected");
    }

    @Test
    void reset_clearsClientState() {
        RateLimiter limiter = new TokenBucketLimiter(2, 0);

        assertTrue(limiter.allowRequest("client1"));
        assertTrue(limiter.allowRequest("client1"));
        assertFalse(limiter.allowRequest("client1"));

        limiter.reset("client1");

        // After reset, should have full capacity again
        assertTrue(limiter.allowRequest("client1"));
    }
}
