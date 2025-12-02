package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;

/**
 * Rate limiting configuration
 */
@Data
@Builder
public class RateLimitConfig {
    private int capacity;           // Maximum tokens in bucket
    private int refillRate;         // Tokens added per interval
    private int intervalSeconds;    // Refill interval in seconds
    private RateLimitingService.RateLimitScope scope;
    private String description;
    @Builder.Default
    private boolean burstAllowed = true;
    
    @Builder.Default
    private int burstCapacity = 0;
    
    public static RateLimitConfig defaultConfig() {
        return RateLimitConfig.builder()
            .capacity(60)
            .refillRate(60)
            .intervalSeconds(60)
            .scope(RateLimitingService.RateLimitScope.IP)
            .description("Default rate limit configuration")
            .burstAllowed(true)
            .burstCapacity(10)
            .build();
    }
    
    public static RateLimitConfig strictConfig() {
        return RateLimitConfig.builder()
            .capacity(10)
            .refillRate(10)
            .intervalSeconds(60)
            .scope(RateLimitingService.RateLimitScope.IP)
            .description("Strict rate limit configuration")
            .burstAllowed(false)
            .burstCapacity(0)
            .build();
    }
    
    public static RateLimitConfig permissiveConfig() {
        return RateLimitConfig.builder()
            .capacity(200)
            .refillRate(100)
            .intervalSeconds(60)
            .scope(RateLimitingService.RateLimitScope.USER)
            .description("Permissive rate limit configuration")
            .burstAllowed(true)
            .burstCapacity(50)
            .build();
    }
    
    public int getTotalCapacity() {
        return burstAllowed ? capacity + burstCapacity : capacity;
    }
}