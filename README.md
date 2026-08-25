# Distributed Rate Limiter

A production-grade **rate limiter** implementation in Java demonstrating multiple algorithms, thread-safe design, and distributed system concepts. Built as a system design exercise targeting FAANG-level interviews.

---

## Architecture

```text
Client Request
      |
      v
+------------------+
|  Rate Limiter    |
|  (Algorithm)     |
+--------+---------+
         |
    +----+----+
    |         |
    v         v
In-Memory    Redis
(Local)      (Distributed)
```

---

## Algorithms Implemented

### 1. Token Bucket
- Tokens added at a fixed rate (e.g., 10 tokens/second)
- Each request consumes one token
- Requests rejected when bucket is empty
- Allows short bursts of traffic

```java
// Usage
RateLimiter limiter = new TokenBucketLimiter(capacity: 10, refillRate: 10, TimeUnit.SECONDS);
boolean allowed = limiter.allowRequest(clientId);
```

### 2. Sliding Window Log
- Tracks exact timestamp of each request in a sorted set
- Removes expired entries outside the window
- Most accurate but memory-intensive
- No boundary issues (unlike fixed window)

```java
RateLimiter limiter = new SlidingWindowLogLimiter(maxRequests: 100, windowSize: 60, TimeUnit.SECONDS);
boolean allowed = limiter.allowRequest(clientId);
```

### 3. Sliding Window Counter
- Hybrid approach: fixed window + weighted previous window
- Memory efficient (only stores counters, not timestamps)
- Smooths boundary spikes
- Used by: Cloudflare, Stripe

```java
RateLimiter limiter = new SlidingWindowCounterLimiter(maxRequests: 100, windowSize: 60, TimeUnit.SECONDS);
boolean allowed = limiter.allowRequest(clientId);
```

### 4. Fixed Window Counter
- Simplest implementation
- Divides time into fixed windows
- Resets counter at each window boundary
- Edge case: 2x burst at window boundaries

```java
RateLimiter limiter = new FixedWindowLimiter(maxRequests: 100, windowSize: 60, TimeUnit.SECONDS);
boolean allowed = limiter.allowRequest(clientId);
```

---

## System Design Considerations

### Thread Safety
- All implementations use `ConcurrentHashMap` for client state
- Atomic operations via `AtomicLong` and `synchronized` blocks
- Lock-free reads where possible (optimistic approach)
- Thread-safe under high concurrency (tested with 1000 concurrent threads)

### Distributed Mode (Redis-backed)
- Redis-based implementation for multi-server deployments
- Lua scripts for atomic check-and-decrement operations
- Consistent across multiple application instances
- Handles Redis failures gracefully (fail-open vs fail-close configurable)

```java
// Distributed usage
RedisRateLimiter limiter = new RedisRateLimiter(redisClient, "api-limiter", 100, 60);
boolean allowed = limiter.allowRequest(clientId);
```

### Configuration
```yaml
rate-limiter:
  algorithm: TOKEN_BUCKET    # TOKEN_BUCKET, SLIDING_WINDOW_LOG, SLIDING_WINDOW_COUNTER, FIXED_WINDOW
  capacity: 100              # Max requests per window
  window-size: 60            # Window size in seconds
  refill-rate: 10            # Tokens per second (Token Bucket only)
  distributed: true          # Use Redis for distributed limiting
  fail-strategy: OPEN        # OPEN (allow) or CLOSE (reject) on Redis failure
```

---

## Algorithm Comparison

| Algorithm | Memory | Accuracy | Burst Handling | Complexity |
|-----------|--------|----------|----------------|------------|
| Token Bucket | O(1) per client | Good | Allows controlled bursts | Low |
| Fixed Window | O(1) per client | Low (boundary spike) | No control | Low |
| Sliding Window Log | O(N) per client | Exact | No bursts | High |
| Sliding Window Counter | O(1) per client | Good (approximation) | Smoothed | Medium |

### When to Use What?

- **Token Bucket**: API rate limiting (AWS, Stripe use this)
- **Sliding Window Counter**: When you need accuracy + efficiency (Cloudflare)
- **Sliding Window Log**: When exact counting is critical (billing, quotas)
- **Fixed Window**: Simple use cases, prototype

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 |
| Build | Maven |
| Distributed Store | Redis (Jedis client) |
| Concurrency | java.util.concurrent |
| Testing | JUnit 5, Mockito |
| CI | GitHub Actions |

---

## Project Structure

```text
src/main/java/com/ratelimiter/
  algorithm/
    TokenBucketLimiter.java
    SlidingWindowLogLimiter.java
    SlidingWindowCounterLimiter.java
    FixedWindowLimiter.java
  distributed/
    RedisRateLimiter.java
    LuaScripts.java
  config/
    RateLimiterConfig.java
  core/
    RateLimiter.java          (interface)
    RateLimiterFactory.java
  exception/
    RateLimitExceededException.java

src/test/java/com/ratelimiter/
  algorithm/
    TokenBucketLimiterTest.java
    SlidingWindowLogLimiterTest.java
    ConcurrencyTest.java
  distributed/
    RedisRateLimiterTest.java
```

---

## Setup and Run

```bash
git clone https://github.com/anshul1822/rate-limiter.git
cd rate-limiter

# Build
mvn clean compile

# Run tests
mvn test

# Run with Redis (for distributed mode)
docker run -d -p 6379:6379 redis:7
mvn spring-boot:run
```

---

## Interview Talking Points

This project demonstrates:
1. **Multiple algorithm trade-offs** - Token Bucket vs Sliding Window vs Fixed Window
2. **Concurrency handling** - Thread-safe implementations with atomic operations
3. **Distributed systems** - Redis-backed limiter with Lua scripts for atomicity
4. **Failure modes** - Fail-open vs fail-close strategies
5. **Clean architecture** - Interface-based design, Factory pattern, Strategy pattern
6. **Testing** - Unit tests, concurrency tests, integration tests

---

## References
- [System Design Interview - Rate Limiter](https://bytebytego.com/courses/system-design-interview/design-a-rate-limiter)
- [Cloudflare Rate Limiting](https://blog.cloudflare.com/counting-things-a-lot-of-different-things/)
- [Stripe Rate Limiting](https://stripe.com/blog/rate-limiters)

---

## License
MIT
