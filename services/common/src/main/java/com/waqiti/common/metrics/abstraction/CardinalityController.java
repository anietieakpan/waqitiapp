package com.waqiti.common.metrics.abstraction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Production-Grade Cardinality Controller
 * Prevents metric cardinality explosion in production
 */
@Slf4j
public class CardinalityController {
    
    private final MetricsConfiguration configuration;
    
    // Track unique metric+tag combinations
    private final Map<String, LongAdder> metricCardinality = new ConcurrentHashMap<>();
    private final AtomicLong totalCardinality = new AtomicLong();
    
    // LRU cache to track recent metric combinations
    private final Cache<String, Long> recentMetrics;
    
    // Cardinality limits by metric name pattern
    private final Map<String, Integer> metricLimits = new ConcurrentHashMap<>();
    
    // Track cardinality violations
    private final LongAdder violationCount = new LongAdder();
    private final Map<String, LongAdder> violationsByMetric = new ConcurrentHashMap<>();
    
    // Adaptive limits based on memory usage
    private volatile double adaptiveFactor = 1.0;
    private long lastAdaptiveCheck = System.currentTimeMillis();
    private static final long ADAPTIVE_CHECK_INTERVAL = 60000; // 1 minute
    
    public CardinalityController(MetricsConfiguration configuration) {
        this.configuration = configuration;
        
        // Initialize LRU cache for recent metrics
        this.recentMetrics = Caffeine.newBuilder()
            .maximumSize(configuration.getMaxCardinality())
            .expireAfterAccess(Duration.ofMinutes(configuration.getCardinalityWindowMinutes()))
            .recordStats()
            .build();
            
        // Initialize default limits
        initializeDefaultLimits();
    }
    
    private void initializeDefaultLimits() {
        // Payment metrics - higher cardinality allowed
        metricLimits.put("payment.*", 5000);
        metricLimits.put("wallet.*", 3000);
        metricLimits.put("transfer.*", 3000);
        
        // User metrics - medium cardinality
        metricLimits.put("user.*", 1000);
        metricLimits.put("kyc.*", 1000);
        metricLimits.put("auth.*", 1000);
        
        // System metrics - lower cardinality
        metricLimits.put("system.*", 500);
        metricLimits.put("database.*", 500);
        metricLimits.put("cache.*", 300);
        
        // API metrics - controlled cardinality
        metricLimits.put("api.*", 2000);
        metricLimits.put("http.*", 1500);
        
        // Default for all others
        metricLimits.put("*", 1000);
    }
    
    /**
     * Check if a metric with given tags is allowed
     */
    public boolean allowMetric(String metricName, TagSet tags) {
        // Critical metrics always allowed
        if (isCriticalMetric(metricName)) {
            trackMetric(metricName, tags);
            return true;
        }
        
        // Check adaptive limits
        checkAdaptiveLimits();
        
        // Create unique key for this metric+tags combination
        String metricKey = metricName + ":" + tags.toCacheKey();
        
        // Check if we've seen this exact combination before (fast path)
        Long existing = recentMetrics.getIfPresent(metricKey);
        if (existing != null) {
            return true; // Already tracked, allow it
        }
        
        // Check cardinality limits
        int limit = getMetricLimit(metricName);
        limit = (int)(limit * adaptiveFactor);
        
        // Get current cardinality for this metric
        LongAdder cardinality = metricCardinality.computeIfAbsent(metricName, k -> new LongAdder());
        long currentCardinality = cardinality.sum();
        
        // Check global limit
        if (totalCardinality.get() >= configuration.getMaxCardinality() * adaptiveFactor) {
            logViolation(metricName, "global_limit", currentCardinality, configuration.getMaxCardinality());
            return false;
        }
        
        // Check metric-specific limit
        if (currentCardinality >= limit) {
            logViolation(metricName, "metric_limit", currentCardinality, limit);
            return false;
        }
        
        // Allow and track this metric
        trackMetric(metricName, tags);
        return true;
    }
    
    /**
     * Track a metric combination
     */
    private void trackMetric(String metricName, TagSet tags) {
        String metricKey = metricName + ":" + tags.toCacheKey();
        
        // Add to recent metrics cache
        recentMetrics.put(metricKey, System.currentTimeMillis());
        
        // Update cardinality counters
        metricCardinality.computeIfAbsent(metricName, k -> new LongAdder()).increment();
        totalCardinality.incrementAndGet();
    }
    
    /**
     * Get limit for a specific metric
     */
    private int getMetricLimit(String metricName) {
        // Check exact match first
        Integer limit = metricLimits.get(metricName);
        if (limit != null) {
            return limit;
        }
        
        // Check pattern matches
        for (Map.Entry<String, Integer> entry : metricLimits.entrySet()) {
            if (metricName.matches(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Return default
        return metricLimits.getOrDefault("*", 1000);
    }
    
    /**
     * Check if metric is critical (never drop)
     */
    private boolean isCriticalMetric(String metricName) {
        return metricName.startsWith("security.") ||
               metricName.startsWith("fraud.") ||
               metricName.startsWith("compliance.") ||
               metricName.startsWith("error.") ||
               metricName.contains(".critical.");
    }
    
    /**
     * Log cardinality violation
     */
    private void logViolation(String metricName, String reason, long current, long limit) {
        violationCount.increment();
        violationsByMetric.computeIfAbsent(metricName, k -> new LongAdder()).increment();
        
        long violations = violationCount.sum();
        if (violations % 1000 == 0) {
            log.warn("Cardinality violation #{} for metric '{}': {} (current={}, limit={})", 
                violations, metricName, reason, current, limit);
            
            // Log top violators
            violationsByMetric.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().sum(), e1.getValue().sum()))
                .limit(5)
                .forEach(entry -> {
                    log.warn("  Top violator: {} with {} violations", 
                        entry.getKey(), entry.getValue().sum());
                });
        }
    }
    
    /**
     * Check and adjust adaptive limits based on system resources
     */
    private void checkAdaptiveLimits() {
        long now = System.currentTimeMillis();
        if (now - lastAdaptiveCheck < ADAPTIVE_CHECK_INTERVAL) {
            return;
        }
        
        lastAdaptiveCheck = now;
        
        // Check memory usage
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsage = (double) usedMemory / maxMemory;
        
        // Adjust adaptive factor based on memory usage
        if (memoryUsage > 0.9) {
            // Critical memory usage - reduce limits
            adaptiveFactor = Math.max(0.5, adaptiveFactor - 0.1);
            log.warn("High memory usage ({}%), reducing cardinality limits to {}%", 
                (int)(memoryUsage * 100), (int)(adaptiveFactor * 100));
        } else if (memoryUsage > 0.75) {
            // High memory usage - maintain or slightly reduce
            adaptiveFactor = Math.max(0.75, adaptiveFactor - 0.05);
        } else if (memoryUsage < 0.5 && adaptiveFactor < 1.0) {
            // Low memory usage - can increase limits
            adaptiveFactor = Math.min(1.0, adaptiveFactor + 0.05);
            log.info("Memory usage normal ({}%), restoring cardinality limits to {}%",
                (int)(memoryUsage * 100), (int)(adaptiveFactor * 100));
        }
        
        // Clean up old entries periodically
        if (totalCardinality.get() > configuration.getMaxCardinality() * 0.8) {
            cleanupOldMetrics();
        }
    }
    
    /**
     * Clean up old metrics from tracking
     */
    private void cleanupOldMetrics() {
        log.info("Running cardinality cleanup, current total: {}", totalCardinality.get());
        
        // Clean up cache (will trigger removal listener)
        recentMetrics.cleanUp();
        
        // Reset counters if needed (only in extreme cases)
        if (totalCardinality.get() > configuration.getMaxCardinality() * 1.5) {
            log.warn("Extreme cardinality detected, performing hard reset");
            reset();
        }
    }
    
    /**
     * Get current cardinality
     */
    public long getCurrentCardinality() {
        return totalCardinality.get();
    }
    
    /**
     * Get cardinality for specific metric
     */
    public long getMetricCardinality(String metricName) {
        LongAdder adder = metricCardinality.get(metricName);
        return adder != null ? adder.sum() : 0;
    }
    
    /**
     * Get violation count
     */
    public long getViolationCount() {
        return violationCount.sum();
    }
    
    /**
     * Get adaptive factor
     */
    public double getAdaptiveFactor() {
        return adaptiveFactor;
    }
    
    /**
     * Reset all cardinality tracking
     */
    public void reset() {
        metricCardinality.clear();
        totalCardinality.set(0);
        violationCount.reset();
        violationsByMetric.clear();
        recentMetrics.invalidateAll();
        adaptiveFactor = 1.0;
        
        log.info("Cardinality controller reset");
    }
    
    /**
     * Get cardinality statistics
     */
    public CardinalityStats getStats() {
        return CardinalityStats.builder()
            .totalCardinality(totalCardinality.get())
            .metricCount(metricCardinality.size())
            .violationCount(violationCount.sum())
            .adaptiveFactor(adaptiveFactor)
            .cacheSize(recentMetrics.estimatedSize())
            .cacheStats(recentMetrics.stats())
            .topMetrics(getTopMetrics(10))
            .build();
    }
    
    /**
     * Get top metrics by cardinality
     */
    private Map<String, Long> getTopMetrics(int count) {
        return metricCardinality.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue().sum(), e1.getValue().sum()))
            .limit(count)
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().sum(),
                (e1, e2) -> e1,
                java.util.LinkedHashMap::new
            ));
    }
}