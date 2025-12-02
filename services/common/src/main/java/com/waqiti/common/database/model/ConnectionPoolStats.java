package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Connection pool statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPoolStats {
    
    public static class ConnectionPoolStatsBuilder {
        public ConnectionPoolStatsBuilder averageConnectionHoldTime(int holdTime) {
            // We'll store this in additionalMetrics
            if (this.additionalMetrics == null) {
                this.additionalMetrics = new java.util.HashMap<>();
            }
            this.additionalMetrics.put("averageConnectionHoldTime", holdTime);
            return this;
        }
    }
    
    /**
     * Pool name
     */
    private String poolName;
    
    /**
     * Total connections
     */
    private int totalConnections;
    
    /**
     * Active connections
     */
    private int activeConnections;
    
    /**
     * Idle connections
     */
    private int idleConnections;
    
    /**
     * Pending connections
     */
    private int pendingConnections;
    
    /**
     * Maximum pool size
     */
    private int maxPoolSize;
    
    /**
     * Minimum pool size
     */
    private int minPoolSize;
    
    /**
     * Connection wait time stats
     */
    private WaitTimeStats waitTimeStats;
    
    /**
     * Connection usage stats
     */
    private UsageStats usageStats;
    
    /**
     * Health status
     */
    private HealthStatus healthStatus;
    
    /**
     * Timestamp
     */
    private Instant timestamp;
    
    /**
     * Additional metrics
     */
    private Map<String, Object> additionalMetrics;
    
    /**
     * Get average connection hold time
     */
    public double getAverageConnectionHoldTime() {
        if (additionalMetrics != null && additionalMetrics.containsKey("averageConnectionHoldTime")) {
            Object value = additionalMetrics.get("averageConnectionHoldTime");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return 0.0;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitTimeStats {
        private double averageWaitTimeMs;
        private double maxWaitTimeMs;
        private double minWaitTimeMs;
        private long totalWaitTimeMs;
        private long waitCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageStats {
        private long connectionsCreated;
        private long connectionsDestroyed;
        private long connectionsAcquired;
        private long connectionsReleased;
        private long connectionTimeouts;
        private long connectionErrors;
        private double utilizationPercentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthStatus {
        private boolean healthy;
        private String status;
        private String lastError;
        private Instant lastErrorTime;
        private Map<String, String> healthChecks;
    }
}