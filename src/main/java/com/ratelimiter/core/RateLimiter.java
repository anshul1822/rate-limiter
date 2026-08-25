package com.ratelimiter.core;

/**
 * Rate Limiter interface - Strategy pattern allowing multiple algorithm implementations.
 * All implementations must be thread-safe for concurrent access.
 */
public interface RateLimiter {

    /**
     * Check if a request from the given client should be allowed.
     *
     * @param clientId unique identifier for the client (IP, API key, user ID)
     * @return true if the request is allowed, false if rate limit exceeded
     */
    boolean allowRequest(String clientId);

    /**
     * Get remaining requests allowed for this client in the current window.
     *
     * @param clientId unique identifier for the client
     * @return number of remaining allowed requests
     */
    long getRemainingRequests(String clientId);

    /**
     * Reset the rate limit state for a specific client.
     *
     * @param clientId unique identifier for the client
     */
    void reset(String clientId);
}
