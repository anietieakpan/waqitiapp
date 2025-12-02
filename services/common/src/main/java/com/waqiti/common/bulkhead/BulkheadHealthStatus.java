package com.waqiti.common.bulkhead;

import lombok.Builder;
import lombok.Data;

/**
 * Overall health status for bulkhead system
 */
@Data
@Builder
public class BulkheadHealthStatus {
    
    private String overall;
    private String paymentProcessingHealth;
    private String kycVerificationHealth;
    private String fraudDetectionHealth;
    private String notificationHealth;
    private String analyticsHealth;
    private String coreBankingHealth;
    
    private double overallUtilization;
    private int totalActiveOperations;
    private int totalCapacity;
    private ResourceType mostUtilizedResource;
    
    public boolean isHealthy() {
        return "HEALTHY".equals(overall);
    }
    
    public boolean isCritical() {
        return "CRITICAL".equals(overall);
    }
    
    public boolean hasWarnings() {
        return "WARNING".equals(overall);
    }
    
    public double getAvailableCapacityPercentage() {
        if (totalCapacity == 0) return 0.0;
        return ((double) (totalCapacity - totalActiveOperations) / totalCapacity) * 100.0;
    }
}