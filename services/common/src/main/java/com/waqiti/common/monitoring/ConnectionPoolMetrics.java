package com.waqiti.common.monitoring;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Database connection pool metrics
 */
@Data
@Builder
public class ConnectionPoolMetrics {
    
    private LocalDateTime timestamp;
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private int threadsAwaitingConnection;
    private int maximumPoolSize;
    private int minimumIdle;
    private long connectionTimeout;
    private long idleTimeout;
    private long maxLifetime;
    private String poolName;
    private boolean canConnect;
    
    public double getUtilizationPercentage() {
        if (maximumPoolSize == 0) return 0;
        return ((double) activeConnections / maximumPoolSize) * 100;
    }
    
    public long getAverageConnectionWaitTime() {
        // This would be calculated from actual wait time metrics
        // For now, estimate based on threads waiting
        if (threadsAwaitingConnection == 0) return 0;
        return threadsAwaitingConnection * 100L; // Rough estimate
    }
    
    public boolean isHealthy() {
        return getUtilizationPercentage() < 80 && 
               threadsAwaitingConnection == 0 &&
               idleConnections > 0;
    }
    
    public String getHealthStatus() {
        if (threadsAwaitingConnection > 5) {
            return "CRITICAL - Connection starvation";
        } else if (getUtilizationPercentage() > 90) {
            return "CRITICAL - Pool near exhaustion";
        } else if (getUtilizationPercentage() > 80) {
            return "WARNING - High utilization";
        } else if (getUtilizationPercentage() > 60) {
            return "MODERATE - Normal load";
        } else {
            return "HEALTHY - Optimal performance";
        }
    }
    
    public int getAvailableConnections() {
        return maximumPoolSize - activeConnections;
    }
}