package com.waqiti.common.bulkhead;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for a single bulkhead resource pool
 */
@Data
@Builder
public class ResourceStats {
    
    private ResourceType resourceType;
    private int poolSize;
    private int available;
    private int inUse;
    private double utilization;
    private int queueLength;
    
    public boolean isHealthy() {
        return utilization < 80.0 && queueLength < 10;
    }
    
    public boolean isOverloaded() {
        return utilization >= 95.0 || queueLength >= 50;
    }
    
    public String getHealthStatus() {
        if (isOverloaded()) {
            return "OVERLOADED";
        } else if (utilization >= 80.0) {
            return "HIGH_UTILIZATION";
        } else if (utilization >= 50.0) {
            return "MODERATE_UTILIZATION";
        } else {
            return "HEALTHY";
        }
    }
}