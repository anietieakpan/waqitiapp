package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents connection pool metrics
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
    private int pendingRequests;
    private long totalConnectionsCreated;
    private long totalConnectionsDestroyed;
    private double averageConnectionWaitTimeMs;
    private double averageConnectionUseTimeMs;
    private Instant metricsTimestamp;
    private ConnectionHealthStatus healthStatus;
    
    // Explicit setter for compilation issues
    public void setHealthStatus(ConnectionHealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }
    
    // Runtime tracking
    private final AtomicLong connectionRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalWaitTime = new AtomicLong(0);
    private final AtomicInteger threadsAwaitingConnection = new AtomicInteger(0);
    
    public ConnectionPoolMetrics(String poolName) {
        this.poolName = poolName;
        this.metricsTimestamp = Instant.now();
    }
    
    public void recordConnectionRequest(long waitTime, boolean successful) {
        connectionRequests.incrementAndGet();
        totalWaitTime.addAndGet(waitTime);
        if (successful) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
        updateAverageWaitTime();
    }
    
    public void updatePoolStatistics(int total, int active, int idle, int awaiting) {
        this.totalConnections = total;
        this.activeConnections = active;
        this.idleConnections = idle;
        this.threadsAwaitingConnection.set(awaiting);
        this.metricsTimestamp = Instant.now();
    }
    
    public void incrementConnectionRequests() {
        connectionRequests.incrementAndGet();
    }
    
    public double getAverageUtilization() {
        if (totalConnections == 0) return 0.0;
        return (double) activeConnections / totalConnections;
    }
    
    private void updateAverageWaitTime() {
        long requests = connectionRequests.get();
        if (requests > 0) {
            this.averageConnectionWaitTimeMs = totalWaitTime.get() / (double) requests;
        }
    }
}