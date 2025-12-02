package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Risk assessment results for payment processing
 * 
 * Contains fraud detection and risk evaluation details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {
    private RiskLevel level;  
    private Double score;  // 0.0 to 1.0
    private RiskOutcome outcome;
    private String reason;
    private List<String> flags;
    private Map<String, Object> riskFactors;
    private String reviewRequired;
    private String riskEngineVersion;
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum RiskOutcome {
        APPROVED,
        MANUAL_REVIEW,
        DECLINED,
        CHALLENGE_REQUIRED,
        ADDITIONAL_VERIFICATION
    }
}