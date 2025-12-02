package com.waqiti.common.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rate limit result model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private int limit;
    private long remaining;
    private long resetTime;
    private long retryAfter;
    
    public static RateLimitResult allowed(int limit, long remaining) {
        return RateLimitResult.builder()
            .allowed(true)
            .limit(limit)
            .remaining(remaining)
            .resetTime(System.currentTimeMillis() + 60000)
            .build();
    }
    
    public static RateLimitResult denied(int limit, long retryAfter) {
        return RateLimitResult.builder()
            .allowed(false)
            .limit(limit)
            .remaining(0)
            .retryAfter(retryAfter)
            .resetTime(System.currentTimeMillis() + retryAfter * 1000)
            .build();
    }
}