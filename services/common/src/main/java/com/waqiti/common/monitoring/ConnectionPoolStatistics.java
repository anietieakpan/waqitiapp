package com.waqiti.common.monitoring;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated connection pool statistics
 */
@Data
@Builder
public class ConnectionPoolStatistics {
    
    private boolean available;
    private ConnectionPoolMetrics current;
    private double averageUtilization;
    private int maxActiveConnectionsObserved;
    private long averageConnectionWaitTime;
    private String healthStatus;
    private String[] recommendations;
    
    public boolean hasRecommendations() {
        return recommendations != null && recommendations.length > 0;
    }
    
    public boolean needsAttention() {
        return healthStatus != null && 
               (healthStatus.contains("WARNING") || healthStatus.contains("CRITICAL"));
    }
}