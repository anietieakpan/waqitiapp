package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fraud Check Result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResult {
    private boolean blocked;
    private boolean flagged;
    private String reason;
    private double riskScore;
    private RiskLevel riskLevel;
    private List<String> riskFactors;
    private Map<String, Object> details;
    private String reviewRequired;
    private List<String> recommendations;
    private Instant checkedAt;
    private String checkId;
    
    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}