package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive email fraud analysis
 */
@Data
@Builder
@Jacksonized
public class EmailFraudAnalysis {
    private String email;
    private String emailAddress;
    private double riskScore;
    private Double confidence; // PRODUCTION FIX: Confidence score for ComprehensiveFraudBlacklistService
    private EmailRiskLevel riskLevel;
    private String riskLevelString;
    private boolean isDisposable;
    private boolean isBlacklisted;
    private boolean hasSuspiciousPattern;
    private boolean inFraudDatabase;
    private String domainReputation;
    private EmailDomainResult domainAnalysis;
    private EmailPatternResult patternAnalysis;
    private EmailVelocityResult velocity;
    private String analysisError;
    private List<String> riskFactors;
    private Map<String, Object> validationResults;
    private Instant lastVerified;

    /**
     * Get email address for analysis
     */
    public String email() {
        return email != null ? email : emailAddress;
    }

    /**
     * PRODUCTION FIX: Get confidence score
     */
    public Double getConfidence() {
        if (confidence != null) {
            return confidence;
        }
        // Calculate confidence based on risk score
        return 1.0 - (riskScore / 100.0);
    }
}