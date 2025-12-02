package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Rate limiting statistics for monitoring and analytics
 */
@Data
@Builder
public class RateLimitStats {
    private String identifier;
    private RateLimitingService.RateLimitScope scope;
    private long tokensUsed;
    private long tokensRemaining;
    private long capacity;
    private LocalDateTime lastRequest;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private long violationCount;
    private boolean isBlocked;
    private String error;
    
    public double getUsagePercentage() {
        if (capacity == 0) return 0.0;
        return (double) tokensUsed / capacity * 100.0;
    }
    
    public boolean isNearLimit() {
        return getUsagePercentage() > 80.0;
    }
    
    public boolean isAtLimit() {
        return tokensRemaining <= 0;
    }
    
    public static RateLimitStats empty(String identifier, RateLimitingService.RateLimitScope scope) {
        return RateLimitStats.builder()
            .identifier(identifier)
            .scope(scope)
            .tokensUsed(0)
            .tokensRemaining(0)
            .capacity(0)
            .violationCount(0)
            .isBlocked(false)
            .build();
    }
    
    public static RateLimitStats error(String identifier, RateLimitingService.RateLimitScope scope, String error) {
        return RateLimitStats.builder()
            .identifier(identifier)
            .scope(scope)
            .error(error)
            .build();
    }
}