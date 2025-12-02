package com.waqiti.common.bulkhead;

import lombok.Builder;
import lombok.Data;

/**
 * Model for capacity check results
 */
@Data
@Builder
public class CapacityCheck {
    
    private ResourceType resourceType;
    private boolean hasCapacity;
    private double utilization;
    private String status;
    
    public boolean isCritical() {
        return "CRITICAL".equals(status);
    }
    
    public boolean isWarning() {
        return "WARNING".equals(status);
    }
    
    public boolean isHealthy() {
        return "HEALTHY".equals(status);
    }
}