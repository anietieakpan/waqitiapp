package com.waqiti.common.performance;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Statistics for query cache performance
 */
@Data
@Builder
public class QueryCacheStatistics {
    
    private long totalHits;
    private long totalMisses;
    private double hitRate;
    private int memoryCacheSize;
    private int memoryCacheMaxSize;
    private int cacheKeyCount;
    private List<CacheKeyStats> topCacheKeys;
    
    public long getTotalRequests() {
        return totalHits + totalMisses;
    }
    
    public double getMemoryCacheUtilization() {
        if (memoryCacheMaxSize == 0) return 0;
        return (double) memoryCacheSize / memoryCacheMaxSize * 100;
    }
    
    public boolean isHealthy() {
        return hitRate > 60 && getMemoryCacheUtilization() < 90;
    }
    
    public String getHealthStatus() {
        if (hitRate < 30) {
            return "POOR - Low hit rate";
        } else if (hitRate < 60) {
            return "MODERATE - Average hit rate";
        } else if (getMemoryCacheUtilization() > 90) {
            return "WARNING - Memory cache near capacity";
        } else {
            return "HEALTHY - Good performance";
        }
    }
}