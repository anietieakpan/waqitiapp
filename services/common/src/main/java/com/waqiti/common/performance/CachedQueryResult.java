package com.waqiti.common.performance;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a cached query result
 */
@Data
@Builder
public class CachedQueryResult {
    
    private Object data;
    private long timestamp;
    private long ttl; // Time to live in milliseconds
    
    public boolean isExpired() {
        return System.currentTimeMillis() > (timestamp + ttl);
    }
    
    public long getRemainingTtl() {
        long remaining = (timestamp + ttl) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    
    public double getTtlPercentageRemaining() {
        if (ttl == 0) return 0;
        return (double) getRemainingTtl() / ttl * 100;
    }
}