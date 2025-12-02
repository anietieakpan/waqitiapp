package com.waqiti.common.ratelimit;

import com.waqiti.common.ratelimit.RateLimitModels.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Result of a rate limit check operation
 * Contains all information about the rate limit status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitCheck {

    private boolean allowed;
    private long remainingTokens;
    private long totalLimit;
    private long windowSizeSeconds;
    private Instant windowResetTime;
    private Instant retryAfterTime;
    private long retryAfterSeconds;
    private String rateLimitKey;
    private String scope;
    private RateLimitStrategy strategy;
    private Map<String, Object> metadata;
    
    // Additional fields for multiple rate limit checks
    private String suffix;
    private RateLimitType type;
    private TokenBucketConfig tokenBucketConfig;
    private SlidingWindowConfig slidingWindowConfig;
    private FixedWindowConfig fixedWindowConfig;

    /**
     * Rate limiting strategies
     */
    public enum RateLimitStrategy {
        TOKEN_BUCKET,      // Token bucket algorithm
        SLIDING_WINDOW,    // Sliding window counter
        FIXED_WINDOW,      // Fixed window counter
        SLIDING_LOG,       // Sliding window log
        LEAKY_BUCKET      // Leaky bucket algorithm
    }

    /**
     * Create an allowed result
     */
    public static RateLimitCheck allowed(long remainingTokens, long totalLimit, String key) {
        return RateLimitCheck.builder()
                .allowed(true)
                .remainingTokens(remainingTokens)
                .totalLimit(totalLimit)
                .rateLimitKey(key)
                .build();
    }

    /**
     * Create a denied result
     */
    public static RateLimitCheck denied(long retryAfterSeconds, String key, String scope) {
        Instant now = Instant.now();
        return RateLimitCheck.builder()
                .allowed(false)
                .remainingTokens(0)
                .retryAfterSeconds(retryAfterSeconds)
                .retryAfterTime(now.plusSeconds(retryAfterSeconds))
                .rateLimitKey(key)
                .scope(scope)
                .build();
    }

    /**
     * Create a detailed result with full information
     */
    public static RateLimitCheck detailed(
            boolean allowed,
            long remainingTokens,
            long totalLimit,
            long windowSizeSeconds,
            Instant windowResetTime,
            String rateLimitKey,
            String scope,
            RateLimitStrategy strategy) {
        
        RateLimitCheckBuilder builder = RateLimitCheck.builder()
                .allowed(allowed)
                .remainingTokens(remainingTokens)
                .totalLimit(totalLimit)
                .windowSizeSeconds(windowSizeSeconds)
                .windowResetTime(windowResetTime)
                .rateLimitKey(rateLimitKey)
                .scope(scope)
                .strategy(strategy);

        if (!allowed && windowResetTime != null) {
            Instant now = Instant.now();
            long retryAfterSeconds = windowResetTime.getEpochSecond() - now.getEpochSecond();
            if (retryAfterSeconds > 0) {
                builder.retryAfterSeconds(retryAfterSeconds)
                       .retryAfterTime(windowResetTime);
            }
        }

        return builder.build();
    }

    /**
     * Check if the request should be allowed
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Check if the request should be denied
     */
    public boolean isDenied() {
        return !allowed;
    }

    /**
     * Get the percentage of quota used
     */
    public double getQuotaUsedPercentage() {
        if (totalLimit == 0) return 0.0;
        return ((double) (totalLimit - remainingTokens) / totalLimit) * 100.0;
    }

    /**
     * Check if quota is exhausted
     */
    public boolean isQuotaExhausted() {
        return remainingTokens <= 0;
    }

    /**
     * Check if quota is nearly exhausted (< 10% remaining)
     */
    public boolean isQuotaNearlyExhausted() {
        return getQuotaUsedPercentage() > 90.0;
    }

    /**
     * Get time until window reset in seconds
     */
    public long getSecondsUntilReset() {
        if (windowResetTime == null) return 0;
        Instant now = Instant.now();
        return Math.max(0, windowResetTime.getEpochSecond() - now.getEpochSecond());
    }

    /**
     * Add metadata information
     */
    public RateLimitCheck withMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Get metadata value
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * Check if metadata exists
     */
    public boolean hasMetadata(String key) {
        return metadata != null && metadata.containsKey(key);
    }

    /**
     * Create headers for HTTP response
     */
    public Map<String, String> toHttpHeaders() {
        Map<String, String> headers = new java.util.HashMap<>();
        
        headers.put("X-RateLimit-Limit", String.valueOf(totalLimit));
        headers.put("X-RateLimit-Remaining", String.valueOf(remainingTokens));
        
        if (windowResetTime != null) {
            headers.put("X-RateLimit-Reset", String.valueOf(windowResetTime.getEpochSecond()));
        }
        
        if (!allowed && retryAfterSeconds > 0) {
            headers.put("Retry-After", String.valueOf(retryAfterSeconds));
        }
        
        if (scope != null) {
            headers.put("X-RateLimit-Scope", scope);
        }
        
        return headers;
    }

    /**
     * Convert to summary string
     */
    public String toSummary() {
        if (allowed) {
            return String.format("ALLOWED: %d/%d remaining", remainingTokens, totalLimit);
        } else {
            return String.format("DENIED: Rate limit exceeded, retry after %d seconds", retryAfterSeconds);
        }
    }
}