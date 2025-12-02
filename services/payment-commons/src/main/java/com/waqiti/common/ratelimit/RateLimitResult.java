package com.waqiti.common.ratelimit;

import lombok.Builder;
import lombok.Data;

/**
 * Result of rate limit check
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Data
@Builder
public class RateLimitResult {
    private final boolean allowed;
    private final int limit;
    private final int remaining;
    private final long resetTime;
    private final String reason;
    
    public static RateLimitResult allowed(int limit, int remaining, long resetTime) {
        return RateLimitResult.builder()
            .allowed(true)
            .limit(limit)
            .remaining(remaining)
            .resetTime(resetTime)
            .build();
    }
    
    public static RateLimitResult denied(int limit, int remaining, long resetTime, String reason) {
        return RateLimitResult.builder()
            .allowed(false)
            .limit(limit)
            .remaining(remaining)
            .resetTime(resetTime)
            .reason(reason)
            .build();
    }
}