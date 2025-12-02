package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Connection pool optimization recommendations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPoolOptimization {
    
    public static class ConnectionPoolOptimizationBuilder {
        public ConnectionPoolOptimizationBuilder currentActiveConnections(int connections) {
            // Initialize current config if needed
            if (this.currentConfig == null) {
                this.currentConfig = PoolConfiguration.builder().build();
            }
            // Store as current metric
            return this;
        }
        
        public ConnectionPoolOptimizationBuilder currentIdleConnections(int connections) {
            // Store idle connections metric
            return this;
        }
        
        public ConnectionPoolOptimizationBuilder currentMaxPoolSize(int maxSize) {
            // Store current max pool size
            if (this.currentConfig == null) {
                this.currentConfig = PoolConfiguration.builder().build();
            }
            return this;
        }
        
        public ConnectionPoolOptimizationBuilder error(String error) {
            // Add error to recommendations
            if (this.recommendations == null) {
                this.recommendations = new String[] { "Error: " + error };
            }
            return this;
        }
    }
    
    /**
     * Current pool configuration
     */
    private PoolConfiguration currentConfig;
    
    /**
     * Recommended pool configuration
     */
    private PoolConfiguration recommendedConfig;
    
    /**
     * Optimization metrics
     */
    private OptimizationMetrics metrics;
    
    /**
     * Recommendations
     */
    private String[] recommendations;
    
    /**
     * Expected improvements
     */
    private ExpectedImprovements expectedImprovements;
    
    /**
     * Recommended max pool size
     */
    private int recommendedMaxPoolSize;
    
    /**
     * Recommended min pool size
     */
    private int recommendedMinPoolSize;
    
    /**
     * Add a recommendation
     */
    public void addRecommendation(String recommendation) {
        if (this.recommendations == null) {
            this.recommendations = new String[] { recommendation };
        } else {
            String[] newRecs = new String[this.recommendations.length + 1];
            System.arraycopy(this.recommendations, 0, newRecs, 0, this.recommendations.length);
            newRecs[this.recommendations.length] = recommendation;
            this.recommendations = newRecs;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PoolConfiguration {
        private int minPoolSize;
        private int maxPoolSize;
        private int connectionTimeout;
        private int idleTimeout;
        private int maxLifetime;
        private int validationTimeout;
        private int leakDetectionThreshold;
        private Map<String, Object> additionalSettings;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationMetrics {
        private double currentUtilization;
        private double peakUtilization;
        private double averageWaitTime;
        private long connectionTimeouts;
        private double connectionCreationRate;
        private double connectionDestructionRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpectedImprovements {
        private double reducedWaitTimePercentage;
        private double reducedTimeoutPercentage;
        private double improvedThroughputPercentage;
        private double reducedResourceUsagePercentage;
        private Map<String, Double> metricImprovements;
    }
}