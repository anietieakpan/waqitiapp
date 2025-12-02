package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprehensive analysis of email domain for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDomainAnalysis {
    
    /**
     * Domain name being analyzed
     */
    private String domain;
    
    /**
     * Whether this is a disposable/temporary email domain
     */
    private boolean isDisposable;
    
    /**
     * Whether the domain exhibits suspicious patterns
     */
    private boolean isSuspicious;
    
    /**
     * Domain age in days
     */
    private int domainAge;
    
    /**
     * Domain reputation score (0.0 to 1.0)
     */
    private double reputation;
    
    /**
     * Whether domain is on any blacklists
     */
    private boolean isBlacklisted;
    
    /**
     * List of blacklist sources if blacklisted
     */
    private List<String> blacklistSources;
    
    /**
     * Domain registrar information
     */
    private String registrar;
    
    /**
     * Country where domain is registered
     */
    private String registrationCountry;
    
    /**
     * Number of fraud reports associated with this domain
     */
    private int fraudReportCount;
    
    /**
     * When this analysis was performed
     */
    private LocalDateTime analyzedAt;
    
    /**
     * Calculate domain risk score
     */
    public double getRiskScore() {
        double score = 0.0;
        
        if (isDisposable) score += 0.4;
        if (isSuspicious) score += 0.3;
        if (isBlacklisted) score += 0.5;
        if (domainAge < 30) score += 0.2; // Very new domains are riskier
        if (fraudReportCount > 0) score += Math.min(0.3, fraudReportCount * 0.1);
        
        // Factor in reputation (lower reputation = higher risk)
        score += (1.0 - reputation) * 0.3;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get risk level for this domain
     */
    public FraudRiskLevel getRiskLevel() {
        double riskScore = getRiskScore();
        if (riskScore >= 0.8) return FraudRiskLevel.CRITICAL;
        if (riskScore >= 0.6) return FraudRiskLevel.HIGH;
        if (riskScore >= 0.4) return FraudRiskLevel.MEDIUM;
        if (riskScore >= 0.2) return FraudRiskLevel.LOW;
        return FraudRiskLevel.MINIMAL;
    }
}