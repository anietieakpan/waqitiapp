package com.waqiti.common.database.performance.models;

import lombok.Data;

import java.time.Instant;

/**
 * Metrics for database connection pool monitoring.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class ConnectionPoolMetrics {
    
    private int activeConnections;
    private int idleConnections;
    private int maxConnections;
    private int waitingRequests;
    private double utilizationPercentage;
    private long averageWaitTime;
    private long maxWaitTime;
    private int connectionFailures;
    private int connectionLeaks;
    private Instant timestamp;
    
    /**
     * Calculate the total number of connections in use.
     *
     * @return total active + idle connections
     */
    public int getTotalConnections() {
        return activeConnections + idleConnections;
    }
    
    /**
     * Determine if the connection pool is healthy.
     *
     * @return true if pool is operating within healthy parameters
     */
    public boolean isHealthy() {
        return utilizationPercentage < 90.0 && 
               waitingRequests < 10 && 
               connectionFailures == 0 &&
               connectionLeaks == 0;
    }
    
    /**
     * Get health status as a descriptive string.
     *
     * @return health status description
     */
    public String getHealthStatus() {
        if (connectionFailures > 0) {
            return "UNHEALTHY - Connection failures detected";
        }
        
        if (connectionLeaks > 0) {
            return "WARNING - Connection leaks detected";
        }
        
        if (utilizationPercentage > 95.0) {
            return "CRITICAL - Pool nearly exhausted";
        }
        
        if (utilizationPercentage > 80.0) {
            return "WARNING - High pool utilization";
        }
        
        if (waitingRequests > 5) {
            return "WARNING - High connection wait queue";
        }
        
        return "HEALTHY";
    }
    
    /**
     * Calculate efficiency of the connection pool.
     *
     * @return efficiency score between 0 and 1
     */
    public double calculateEfficiency() {
        if (maxConnections == 0) {
            return 0.0;
        }
        
        // Ideal utilization is around 70-80%
        double utilizationScore = 1.0 - Math.abs(0.75 - (utilizationPercentage / 100.0));
        
        // Penalize for waiting requests
        double waitingPenalty = Math.max(0, waitingRequests * 0.1);
        
        // Penalize for failures and leaks
        double failurePenalty = (connectionFailures + connectionLeaks) * 0.2;
        
        return Math.max(0.0, utilizationScore - waitingPenalty - failurePenalty);
    }
}