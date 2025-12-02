package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Rate limiting result with detailed information
 */
@Data
@Builder
public class RateLimitResult {
    private boolean allowed;
    private long remainingTokens;
    private long capacity;
    private LocalDateTime resetTime;
    private long retryAfterSeconds;
    private String rateLimitKey;
    private String reason;
    
    public static RateLimitResult allowed() {
        return RateLimitResult.builder()
            .allowed(true)
            .remainingTokens(Long.MAX_VALUE)
            .capacity(Long.MAX_VALUE)
            .retryAfterSeconds(0)
            .reason("Rate limiting disabled or no limits applied")
            .build();
    }
    
    public static RateLimitResult denied(long retryAfterSeconds, String reason) {
        return RateLimitResult.builder()
            .allowed(false)
            .remainingTokens(0)
            .retryAfterSeconds(retryAfterSeconds)
            .reason(reason)
            .build();
    }
    
    public boolean isBlocked() {
        return !allowed;
    }
    
    public boolean hasTokensRemaining() {
        return remainingTokens > 0;
    }
}