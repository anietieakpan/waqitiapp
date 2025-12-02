package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for fraud check results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResult {
    
    private boolean blocked;
    private String riskLevel; // LOW, MEDIUM, HIGH
    private String reason;
    
    @Builder.Default
    private boolean requiresAdditionalVerification = false;
    
    private String recommendedAction;
    private Double riskScore; // 0.0 to 1.0
    
    // Additional details for audit/investigation
    private String ruleTriggered;
    private String investigationId;
    private java.util.Map<String, Object> metadata;
    
    public boolean isBlocked() {
        return blocked;
    }
    
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel);
    }
    
    public boolean requiresManualReview() {
        return requiresAdditionalVerification || isHighRisk();
    }
}