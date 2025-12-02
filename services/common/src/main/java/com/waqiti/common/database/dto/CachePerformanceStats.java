package com.waqiti.common.database.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Cache performance statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachePerformanceStats {
    
    private String cacheName;
    private long totalRequests;
    private long cacheHits;
    private long cacheMisses;
    private long evictions;
    private long loadCount;
    private Duration totalLoadTime;
    private Duration averageLoadTime;
    private long currentSize;
    private long maxSize;
    private double hitRate;
    private double missRate;
    private double evictionRate;
    private Instant lastResetTime;
    private Instant lastAccessTime;
    private long memoryUsageBytes;
    private int concurrencyLevel;
    private Duration expireAfterWrite;
    private Duration expireAfterAccess;
    private Map<String, Double> hitRateByQueryType;
    
    /**
     * Calculate hit rate percentage
     */
    public double getHitRatePercentage() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) cacheHits / totalRequests * 100.0;
    }
    
    /**
     * Calculate miss rate percentage
     */
    public double getMissRatePercentage() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) cacheMisses / totalRequests * 100.0;
    }
    
    /**
     * Calculate cache utilization
     */
    public double getCacheUtilization() {
        if (maxSize == 0) {
            return 0.0;
        }
        return (double) currentSize / maxSize * 100.0;
    }
    
    /**
     * Calculate eviction rate percentage
     */
    public double getEvictionRatePercentage() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) evictions / totalRequests * 100.0;
    }
    
    /**
     * Calculate average load time in milliseconds
     */
    public double getAverageLoadTimeMs() {
        if (loadCount == 0) {
            return 0.0;
        }
        return totalLoadTime.toMillis() / (double) loadCount;
    }
    
    /**
     * Check if cache is performing well
     */
    public boolean isPerformingWell() {
        return getHitRatePercentage() > 80.0 && 
               getEvictionRatePercentage() < 5.0 &&
               getAverageLoadTimeMs() < 100.0;
    }
    
    /**
     * Check if cache needs tuning
     */
    public boolean needsTuning() {
        return getHitRatePercentage() < 60.0 || 
               getEvictionRatePercentage() > 10.0 ||
               getCacheUtilization() > 95.0;
    }
    
    /**
     * Check if cache size should be increased
     */
    public boolean shouldIncreaseSize() {
        return getEvictionRatePercentage() > 5.0 && 
               getCacheUtilization() > 90.0 &&
               getHitRatePercentage() > 70.0;
    }
    
    /**
     * Check if cache size can be decreased
     */
    public boolean canDecreaseSize() {
        return getCacheUtilization() < 50.0 && 
               getEvictionRatePercentage() < 1.0;
    }
    
    /**
     * Get performance status
     */
    public CachePerformanceStatus getPerformanceStatus() {
        if (isPerformingWell()) {
            return CachePerformanceStatus.EXCELLENT;
        } else if (getHitRatePercentage() > 70.0) {
            return CachePerformanceStatus.GOOD;
        } else if (getHitRatePercentage() > 50.0) {
            return CachePerformanceStatus.FAIR;
        } else {
            return CachePerformanceStatus.POOR;
        }
    }
    
    public enum CachePerformanceStatus {
        EXCELLENT, GOOD, FAIR, POOR
    }
}