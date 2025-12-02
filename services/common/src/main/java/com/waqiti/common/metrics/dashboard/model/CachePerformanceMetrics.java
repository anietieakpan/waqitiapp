package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Comprehensive cache performance metrics for production monitoring
 * Tracks hit rates, memory usage, and cache efficiency across all cache regions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachePerformanceMetrics {
    // Core performance metrics
    private Double hitRate;
    private Double missRate;
    private Long totalHits;
    private Long totalMisses;
    private Long totalRequests;
    
    // Cache operations
    private Long evictions;
    private Double evictionRate; // Evictions per second or percentage
    private Long expiredEntries;
    private Long replacements;
    private Long loads;
    private Long puts;
    private Long gets;
    private Long removes;
    
    // Performance timing
    private Double avgLoadTime;
    private Double maxLoadTime;
    private Double minLoadTime;
    private Double avgEvictionTime;
    
    // Memory and capacity
    private Long memoryUsage;
    private Long maxMemory;
    private Double memoryUtilization;
    private Long entryCount;
    private Long maxEntries;
    private Double capacityUtilization;
    
    // Regional breakdown
    private Map<String, Double> hitRateByRegion;
    private Map<String, Long> memoryByRegion;
    private Map<String, Long> entriesByRegion;
    
    // Advanced metrics
    private Map<String, Object> distributionStats;
    private Map<String, Double> performanceBreakdown;
    private Double cacheEfficiencyScore;
}