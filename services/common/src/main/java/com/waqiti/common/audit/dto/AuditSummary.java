package com.waqiti.common.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Summary of audit activities and metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSummary {
    
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private long totalEvents;
    private long securityEvents;
    private long dataAccessEvents;
    private long configurationChanges;
    private long failedAttempts;
    private Map<String, Long> eventsByCategory;
    private Map<String, Long> eventsByUser;
    private String riskLevel;
    
    /**
     * Calculate audit health score
     */
    public double calculateAuditHealthScore() {
        if (totalEvents == 0) return 100.0;
        
        double failureRate = (double) failedAttempts / totalEvents * 100.0;
        double securityEventRate = (double) securityEvents / totalEvents * 100.0;
        
        double score = 100.0;
        score -= failureRate * 2.0; // Penalize failures
        score -= Math.min(securityEventRate * 0.5, 20.0); // Penalize security events but cap at 20
        
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    /**
     * Determine risk level based on audit data
     */
    public String determineRiskLevel() {
        double healthScore = calculateAuditHealthScore();
        
        if (healthScore >= 90) return "LOW";
        else if (healthScore >= 70) return "MEDIUM";
        else if (healthScore >= 50) return "HIGH";
        else return "CRITICAL";
    }
}