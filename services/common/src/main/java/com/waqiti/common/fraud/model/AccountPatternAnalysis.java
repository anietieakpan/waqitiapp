package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Analysis of account number patterns for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountPatternAnalysis {
    
    /**
     * Whether account number contains sequential digits
     */
    private boolean isSequential;
    
    /**
     * Whether account number has repeated digits
     */
    private boolean hasRepeatedDigits;
    
    /**
     * Whether account number matches test patterns
     */
    private boolean isTestPattern;
    
    /**
     * Whether account number appears to be randomly generated
     */
    private boolean appearsRandom;
    
    /**
     * Length of the account number
     */
    private int length;
    
    /**
     * Number of unique digits in the account number
     */
    private int uniqueDigitCount;
    
    /**
     * Whether account follows standard industry formatting
     */
    private boolean followsStandardFormat;
    
    /**
     * Luhn checksum validation result (for applicable account types)
     */
    private boolean passesLuhnCheck;
    
    /**
     * List of suspicious patterns detected
     */
    private List<String> detectedPatterns;
    
    /**
     * When this analysis was performed
     */
    private LocalDateTime analyzedAt;
    
    /**
     * Calculate account pattern risk score
     */
    public double getRiskScore() {
        double score = 0.0;
        
        if (isTestPattern) score += 0.5;
        if (isSequential) score += 0.4;
        if (hasRepeatedDigits && uniqueDigitCount <= 3) score += 0.3;
        if (!followsStandardFormat) score += 0.2;
        if (!passesLuhnCheck && shouldPassLuhnCheck()) score += 0.3;
        if (length < 8 || length > 19) score += 0.1;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Determine if this account type should pass Luhn check
     */
    private boolean shouldPassLuhnCheck() {
        // Most card numbers should pass Luhn check
        return length >= 13 && length <= 19;
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