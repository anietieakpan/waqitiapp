package com.waqiti.common.metrics.abstraction;

import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Production-Grade Metrics Configuration
 * Central configuration for all metrics behavior
 */
@Data
@Builder
@Component
@ConfigurationProperties(prefix = "waqiti.metrics")
public class MetricsConfiguration {
    
    @Builder.Default
    private boolean enabled = true;
    
    @Builder.Default
    private int maxCardinality = 10000;
    
    @Builder.Default
    private int cardinalityWindowMinutes = 60;
    
    @Builder.Default
    private double defaultSampleRate = 1.0;
    
    @Builder.Default
    private boolean enableAdaptiveLimits = true;
    
    @Builder.Default
    private boolean enableCircuitBreaker = true;
    
    @Builder.Default
    private int circuitBreakerThreshold = 100;
    
    @Builder.Default
    private Duration circuitBreakerTimeout = Duration.ofSeconds(30);
    
    @Builder.Default
    private boolean enableMetricValidation = true;
    
    @Builder.Default
    private boolean enableMetricCaching = true;
    
    @Builder.Default
    private int metricCacheSize = 10000;
    
    @Builder.Default
    private boolean logMetricErrors = true;
    
    @Builder.Default
    private boolean enableMetaMetrics = true;
    
    @Builder.Default
    private boolean enableHighCardinalityMetrics = false;
    
    @Builder.Default
    private Duration metricPublishInterval = Duration.ofMinutes(1);
    
    @Builder.Default
    private String metricPrefix = "waqiti";
    
    @Builder.Default
    private boolean includeHostTag = true;
    
    @Builder.Default
    private boolean includeEnvironmentTag = true;
    
    @Builder.Default
    private boolean includeServiceTag = true;
    
    @Builder.Default
    private boolean enableDebugMetrics = false;
    
    /**
     * Get effective sample rate for a metric
     */
    public double getSampleRate(String metricName) {
        // Override sample rates for specific metrics
        if (metricName.startsWith("debug.")) {
            return enableDebugMetrics ? 1.0 : 0.0;
        }
        if (metricName.contains(".trace.")) {
            return 0.1; // 10% sampling for trace metrics
        }
        if (metricName.contains(".detail.")) {
            return 0.01; // 1% sampling for detailed metrics
        }
        return defaultSampleRate;
    }
    
    /**
     * Check if a metric should be published
     */
    public boolean shouldPublish(String metricName) {
        if (!enabled) {
            return false;
        }
        
        if (metricName.startsWith("debug.") && !enableDebugMetrics) {
            return false;
        }
        
        if (metricName.contains(".high_cardinality.") && !enableHighCardinalityMetrics) {
            return false;
        }
        
        return true;
    }
}