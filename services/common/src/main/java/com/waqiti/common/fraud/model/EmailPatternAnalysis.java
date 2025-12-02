package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Analysis of email address patterns for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailPatternAnalysis {
    
    /**
     * Whether email contains numbers
     */
    private boolean hasNumbers;
    
    /**
     * Whether email has special characters beyond standard ones
     */
    private boolean hasSpecialChars;
    
    /**
     * Length of the email address
     */
    private int length;
    
    /**
     * Whether email matches known suspicious patterns
     */
    private boolean suspiciousPattern;
    
    /**
     * Whether email contains plus addressing (e.g., user+tag@domain.com)
     */
    private boolean hasPlusAddressing;
    
    /**
     * Whether email uses subdomain addressing
     */
    private boolean hasSubdomainAddressing;
    
    /**
     * Number of dots in the local part
     */
    private int dotsInLocalPart;
    
    /**
     * Whether email looks like it was generated automatically
     */
    private boolean appearsGenerated;
    
    /**
     * List of suspicious patterns detected
     */
    private List<String> detectedPatterns;
    
    /**
     * When this analysis was performed
     */
    private LocalDateTime analyzedAt;
    
    /**
     * Calculate pattern-based risk score
     */
    public double getRiskScore() {
        double score = 0.0;
        
        if (suspiciousPattern) score += 0.4;
        if (appearsGenerated) score += 0.3;
        if (hasPlusAddressing && hasNumbers) score += 0.2;
        if (length < 5 || length > 50) score += 0.1;
        if (dotsInLocalPart > 3) score += 0.1;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get risk level based on pattern analysis
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