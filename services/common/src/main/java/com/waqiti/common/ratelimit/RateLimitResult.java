package com.waqiti.common.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rate Limit Check Result
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitResult {
    
    private boolean allowed;
    private String identifier;
    private String algorithm;
    private long remainingTokens;
    private long remainingRequests;
    private long currentCount;
    private long resetTimeMs;
    private String error;
    
    public boolean isRateLimited() {
        return !allowed;
    }
    
    public long getResetTimeSeconds() {
        return resetTimeMs / 1000;
    }
    
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }
}