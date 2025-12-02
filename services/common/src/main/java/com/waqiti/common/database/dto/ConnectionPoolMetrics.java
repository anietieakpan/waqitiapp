package com.waqiti.common.database.dto;

import com.waqiti.common.database.ConnectionHealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Connection pool performance metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPoolMetrics {
    
    private String poolName;
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private int maxConnections;
    private int minConnections;
    private long totalConnectionsCreated;
    private long totalConnectionsDestroyed;
    private long totalConnectionRequests;
    private long totalConnectionTimeouts;
    private Duration averageConnectionWaitTime;
    private Duration maxConnectionWaitTime;
    private Duration averageConnectionLifetime;
    private Instant lastConnectionCreated;
    private Instant lastConnectionDestroyed;
    private double connectionUtilization;
    private long pendingConnections;
    private int validationErrors;
    private long leakedConnections;
    private ConnectionHealthStatus healthStatus;
    private int waitingRequests;
    private double utilizationPercentage;
    private Instant timestamp;
    
    /**
     * Calculate pool utilization percentage
     */
    public double getPoolUtilization() {
        if (maxConnections == 0) {
            return 0.0;
        }
        return (double) totalConnections / maxConnections * 100.0;
    }
    
    /**
     * Calculate active connection percentage
     */
    public double getActiveConnectionPercentage() {
        if (totalConnections == 0) {
            return 0.0;
        }
        return (double) activeConnections / totalConnections * 100.0;
    }
    
    /**
     * Calculate connection turnover rate
     */
    public double getConnectionTurnoverRate() {
        if (totalConnectionsCreated == 0) {
            return 0.0;
        }
        return (double) totalConnectionsDestroyed / totalConnectionsCreated;
    }
    
    /**
     * Calculate timeout rate
     */
    public double getTimeoutRate() {
        if (totalConnectionRequests == 0) {
            return 0.0;
        }
        return (double) totalConnectionTimeouts / totalConnectionRequests * 100.0;
    }
    
    /**
     * Check if pool is under pressure
     */
    public boolean isUnderPressure() {
        return getPoolUtilization() > 80.0 || 
               pendingConnections > 0 || 
               getTimeoutRate() > 1.0 ||
               averageConnectionWaitTime.toMillis() > 100;
    }
    
    /**
     * Check if pool needs scaling up
     */
    public boolean needsScalingUp() {
        return getPoolUtilization() > 90.0 && 
               activeConnections == totalConnections &&
               pendingConnections > 0;
    }
    
    /**
     * Check if pool can be scaled down
     */
    public boolean canScaleDown() {
        return getActiveConnectionPercentage() < 30.0 && 
               totalConnections > minConnections &&
               pendingConnections == 0;
    }
    
    /**
     * Get health status
     */
    public PoolHealthStatus getHealthStatus() {
        if (leakedConnections > 0 || validationErrors > 5) {
            return PoolHealthStatus.CRITICAL;
        }
        
        if (isUnderPressure()) {
            return PoolHealthStatus.WARNING;
        }
        
        if (getPoolUtilization() > 50.0) {
            return PoolHealthStatus.GOOD;
        }
        
        return PoolHealthStatus.EXCELLENT;
    }
    
    public enum PoolHealthStatus {
        EXCELLENT, GOOD, WARNING, CRITICAL
    }
}