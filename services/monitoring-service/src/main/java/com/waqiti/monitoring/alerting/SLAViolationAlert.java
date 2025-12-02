package com.waqiti.monitoring.alerting;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * CRITICAL MONITORING: SLA Violation Alert Data Structure
 * PRODUCTION-READY: Alert information for SLA violations
 */
@Data
@Builder
public class SLAViolationAlert {
    
    private String alertId;
    private String slaType;
    private double targetSLA;
    private double currentSLA;
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    private LocalDateTime timestamp;
    private String message;
    private Map<String, Object> metadata;
    
    /**
     * Get SLA deviation percentage
     */
    public double getSLADeviation() {
        return targetSLA - currentSLA;
    }
    
    /**
     * Check if this is a critical alert
     */
    public boolean isCritical() {
        return "CRITICAL".equals(severity);
    }
    
    /**
     * Get alert priority score for routing
     */
    public int getPriorityScore() {
        switch (severity) {
            case "CRITICAL": return 4;
            case "HIGH": return 3;
            case "MEDIUM": return 2;
            case "LOW": return 1;
            default: return 0;
        }
    }
}