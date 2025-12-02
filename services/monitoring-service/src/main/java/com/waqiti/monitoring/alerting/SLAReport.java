package com.waqiti.monitoring.alerting;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CRITICAL MONITORING: SLA Report Data Structure
 * PRODUCTION-READY: Comprehensive SLA reporting metrics
 */
@Data
@Builder
public class SLAReport {
    
    private String slaType;
    private LocalDateTime timestamp;
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private double successRate;
    private double averageResponseTime;
    private double totalTransactionValue;
    private boolean slaCompliant;
    
    /**
     * Get uptime percentage
     */
    public double getUptimePercentage() {
        return successRate;
    }
    
    /**
     * Get error rate percentage
     */
    public double getErrorRate() {
        return 100.0 - successRate;
    }
    
    /**
     * Check if this is a critical SLA violation
     */
    public boolean isCriticalViolation(double targetSLA) {
        return !slaCompliant && (targetSLA - successRate) > 5.0;
    }
}