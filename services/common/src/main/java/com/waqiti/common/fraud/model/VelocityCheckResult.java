package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of velocity check for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityCheckResult {
    
    private boolean violationDetected;
    private String violationReason;
    private double riskScore;
    private int transactionCount;
    private int timeWindowMinutes;
    
    /**
     * Create a violation result
     */
    public static VelocityCheckResult violation(String reason, double riskScore) {
        return VelocityCheckResult.builder()
            .violationDetected(true)
            .violationReason(reason)
            .riskScore(riskScore)
            .build();
    }
    
    /**
     * Create a clean result
     */
    public static VelocityCheckResult clean() {
        return VelocityCheckResult.builder()
            .violationDetected(false)
            .riskScore(0.0)
            .build();
    }
    
    /**
     * Create a passed result (alias for clean)
     */
    public static VelocityCheckResult passed() {
        return clean();
    }
    
    /**
     * Check if velocity check has violation
     */
    public boolean isViolation() {
        return violationDetected;
    }
    
    /**
     * Get description of the velocity check result
     */
    public String getDescription() {
        return violationReason != null ? violationReason : 
               (violationDetected ? "Velocity violation detected" : "Velocity check passed");
    }
}