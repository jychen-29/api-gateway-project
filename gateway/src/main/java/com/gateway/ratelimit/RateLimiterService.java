package com.gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limit.default-user-limit:100}")
    private int defaultUserLimit;

    @Value("${rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("#{${rate-limit.default-endpoint-limits:{'/api/users':200,'/api/orders':150,'/api/products':300}}}")
    private Map<String, Integer> endpointLimits;

    /**
     * Check and increment rate limit for a user.
     * Uses sliding window counter backed by Redis.
     *
     * @return RateLimitResult with allowed flag and remaining count
     */
    public RateLimitResult checkUserLimit(String userId) {
        String key = "rl:user:" + userId;
        return checkLimit(key, defaultUserLimit);
    }

    /**
     * Check and increment rate limit for an endpoint.
     */
    public RateLimitResult checkEndpointLimit(String endpoint) {
        // Normalize path to match configured prefixes
        String normalizedPath = normalizePath(endpoint);
        int limit = endpointLimits.getOrDefault(normalizedPath, defaultUserLimit * 3);
        String key = "rl:endpoint:" + normalizedPath;
        return checkLimit(key, limit);
    }

    private RateLimitResult checkLimit(String key, int limit) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return new RateLimitResult(true, limit, limit);
            }
            // Set expiry on first increment
            if (count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            boolean allowed = count <= limit;
            int remaining = (int) Math.max(0, limit - count);
            log.debug("Rate limit check: key={}, count={}, limit={}, allowed={}", key, count, limit, allowed);
            return new RateLimitResult(allowed, remaining, limit);
        } catch (Exception e) {
            // Fail open: if Redis is unavailable, allow the request
            log.error("Redis unavailable for rate limiting, failing open: {}", e.getMessage());
            return new RateLimitResult(true, -1, limit);
        }
    }

    private String normalizePath(String path) {
        for (String prefix : endpointLimits.keySet()) {
            if (path.startsWith(prefix)) return prefix;
        }
        return path;
    }

    public record RateLimitResult(boolean allowed, int remaining, int limit) {}
}
