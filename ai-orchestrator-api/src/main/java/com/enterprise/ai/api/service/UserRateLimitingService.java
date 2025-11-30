package com.enterprise.ai.api.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user rate limiting service for runtime protection.
 * Prevents abuse by limiting requests per user.
 */
@Slf4j
@Service
public class UserRateLimitingService {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final ConcurrentHashMap<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();
    
    @Value("${rate-limit.requests-per-minute:30}")
    private int requestsPerMinute;
    
    @Value("${rate-limit.timeout-seconds:5}")
    private int timeoutSeconds;

    public UserRateLimitingService() {
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitForPeriod(30)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        
        this.rateLimiterRegistry = RateLimiterRegistry.of(defaultConfig);
    }

    /**
     * Check if user is allowed to make a request.
     *
     * @param userId user identifier
     * @return true if request is allowed, false if rate limited
     */
    public boolean isAllowed(String userId) {
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        
        RateLimiter rateLimiter = getOrCreateRateLimiter(userId);
        boolean permitted = rateLimiter.acquirePermission();
        
        if (!permitted) {
            log.warn("User {} is rate limited", userId);
        }
        
        return permitted;
    }

    /**
     * Get remaining permits for a user.
     *
     * @param userId user identifier
     * @return number of remaining permits
     */
    public int getRemainingPermits(String userId) {
        RateLimiter rateLimiter = getOrCreateRateLimiter(userId);
        return rateLimiter.getMetrics().getAvailablePermissions();
    }

    /**
     * Reset rate limit for a user (admin function).
     *
     * @param userId user identifier
     */
    public void resetRateLimit(String userId) {
        userRateLimiters.remove(userId);
        log.info("Rate limit reset for user: {}", userId);
    }

    /**
     * Update rate limit configuration.
     *
     * @param requestsPerMinute new limit
     */
    public void updateLimit(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
        userRateLimiters.clear(); // Force recreation with new config
        log.info("Rate limit updated to {} requests per minute", requestsPerMinute);
    }

    private RateLimiter getOrCreateRateLimiter(String userId) {
        return userRateLimiters.computeIfAbsent(userId, id -> {
            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitForPeriod(requestsPerMinute)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ofSeconds(timeoutSeconds))
                    .build();
            
            return rateLimiterRegistry.rateLimiter(id, config);
        });
    }
}
