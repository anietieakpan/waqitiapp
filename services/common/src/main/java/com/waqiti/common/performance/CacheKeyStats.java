package com.waqiti.common.performance;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for individual cache keys
 */
@Data
@Builder
public class CacheKeyStats {
    
    private String key;
    private long hits;
    private long misses;
    private double hitRate;
    
    public long getTotalAccess() {
        return hits + misses;
    }
    
    public boolean isEffective() {
        return hitRate > 70;
    }
    
    public String getEffectiveness() {
        if (hitRate > 90) {
            return "EXCELLENT";
        } else if (hitRate > 70) {
            return "GOOD";
        } else if (hitRate > 50) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }
}