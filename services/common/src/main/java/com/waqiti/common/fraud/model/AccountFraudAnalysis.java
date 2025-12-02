package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Account fraud analysis
 */
@Data
@Builder
@Jacksonized
public class AccountFraudAnalysis {
    private String accountId;
    private String accountInfo;
    private String userId;
    private double riskScore;
    private Double confidence; // PRODUCTION FIX: Confidence score for ComprehensiveFraudBlacklistService
    private AccountRiskLevel riskLevel;
    private String accountStatus;
    private int accountAgeDays;
    private boolean hasHistoricalFraud;
    private boolean inFraudDatabase;
    private double transactionVelocity;
    private AccountPatternResult patternAnalysis;
    private AccountVelocityResult velocity;
    private AccountValidationResult validation;
    private String analysisError;
    private List<String> suspiciousActivities;
    private Map<String, Object> accountMetrics;
    private Instant lastRiskAssessment;

    /**
     * Get account information
     */
    public String accountInfo() {
        return accountInfo != null ? accountInfo : accountId;
    }

    /**
     * PRODUCTION FIX: Get confidence score
     */
    public Double getConfidence() {
        if (confidence != null) {
            return confidence;
        }
        // Calculate confidence based on account age and risk
        double baseConfidence = 1.0 - (riskScore / 100.0);
        if (accountAgeDays > 365) {
            baseConfidence *= 1.1; // More confidence for older accounts
        }
        return Math.min(1.0, baseConfidence);
    }
}