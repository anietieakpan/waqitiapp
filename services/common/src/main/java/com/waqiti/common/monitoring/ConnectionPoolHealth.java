package com.waqiti.common.monitoring;

import lombok.Builder;
import lombok.Data;

/**
 * Connection pool health status
 */
@Data
@Builder
public class ConnectionPoolHealth {
    
    private String status;
    private boolean healthy;
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private double utilization;
    private int threadsWaiting;
    private String message;
    
    public boolean isCritical() {
        return status != null && status.contains("CRITICAL");
    }
    
    public boolean hasWarning() {
        return status != null && status.contains("WARNING");
    }
    
    public boolean isOptimal() {
        return healthy && utilization < 50 && threadsWaiting == 0;
    }
}