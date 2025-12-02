package com.waqiti.common.ratelimit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Rate limit result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private long remainingRequests;
    private long resetTimeEpoch;
    private long retryAfterSeconds;
    private String limitType;
    private String reason;
    
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();
    
    public static RateLimitResult allowed(long remainingRequests, long resetTimeEpoch) {
        return RateLimitResult.builder()
                .allowed(true)
                .remainingRequests(remainingRequests)
                .resetTimeEpoch(resetTimeEpoch)
                .build();
    }
    
    public static RateLimitResult denied(long retryAfterSeconds, String reason) {
        return RateLimitResult.builder()
                .allowed(false)
                .retryAfterSeconds(retryAfterSeconds)
                .reason(reason)
                .build();
    }
}