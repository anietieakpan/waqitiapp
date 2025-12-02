package com.waqiti.common.database.dto;

import lombok.Data;

/**
 * Connection pool status information.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class ConnectionPoolStatus {
    private boolean healthy;
    private int totalConnections;
    private int activeConnections;
    private int idleConnections;
    private int waitingThreads;
    private int maxConnections;
    private double utilizationPercentage;
    private int waitingRequests;
    
    public ConnectionPoolStatus() {}
    
    public ConnectionPoolStatus(boolean healthy, int totalConnections, int activeConnections, 
                              int idleConnections, int waitingThreads) {
        this.healthy = healthy;
        this.totalConnections = totalConnections;
        this.activeConnections = activeConnections;
        this.idleConnections = idleConnections;
        this.waitingThreads = waitingThreads;
    }
    
    public ConnectionPoolStatus(boolean healthy, int totalConnections, int activeConnections, 
                              int idleConnections, int waitingThreads, int maxConnections,
                              double utilizationPercentage, int waitingRequests) {
        this.healthy = healthy;
        this.totalConnections = totalConnections;
        this.activeConnections = activeConnections;
        this.idleConnections = idleConnections;
        this.waitingThreads = waitingThreads;
        this.maxConnections = maxConnections;
        this.utilizationPercentage = utilizationPercentage;
        this.waitingRequests = waitingRequests;
    }
}