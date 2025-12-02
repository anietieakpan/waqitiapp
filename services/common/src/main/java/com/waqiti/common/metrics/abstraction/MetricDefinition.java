package com.waqiti.common.metrics.abstraction;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-Grade Metric Definition
 * Defines a metric with all its configuration and constraints
 */
@Data
@Builder
public class MetricDefinition {
    
    private final String name;
    private final String description;
    private final MetricType type;
    
    @Builder.Default
    private final boolean enabled = true;
    
    @Builder.Default
    private final double sampleRate = 1.0; // 1.0 = 100% sampling
    
    @Builder.Default
    private final List<Duration> slos = new ArrayList<>(); // Service Level Objectives for timers
    
    @Builder.Default
    private final double scale = 1.0; // Scale factor for distribution summaries
    
    @Builder.Default
    private final int maxCardinality = 1000; // Max unique tag combinations for this metric
    
    @Builder.Default
    private final boolean critical = false; // If true, never drop this metric
    
    @Builder.Default
    private final TagConstraints tagConstraints = TagConstraints.DEFAULT;
    
    public enum MetricType {
        COUNTER,
        TIMER,
        GAUGE,
        DISTRIBUTION_SUMMARY,
        LONG_TASK_TIMER
    }
    
    /**
     * Validate metric definition
     */
    public void validate() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Metric name cannot be null or empty");
        }
        
        if (!name.matches("^[a-z][a-z0-9._]*$")) {
            throw new IllegalArgumentException("Metric name must match pattern: ^[a-z][a-z0-9._]*$");
        }
        
        if (type == null) {
            throw new IllegalArgumentException("Metric type cannot be null");
        }
        
        if (sampleRate < 0 || sampleRate > 1) {
            throw new IllegalArgumentException("Sample rate must be between 0 and 1");
        }
        
        if (maxCardinality < 1) {
            throw new IllegalArgumentException("Max cardinality must be at least 1");
        }
    }
    
    /**
     * Check if this is a high-cardinality metric
     */
    public boolean isHighCardinality() {
        return maxCardinality > 100;
    }
    
    /**
     * Check if this metric should be sampled
     */
    public boolean shouldSample() {
        return sampleRate < 1.0;
    }
}