package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of account pattern analysis for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountPatternResult {
    
    private String accountNumber;
    private String accountType;
    private PatternRiskLevel riskLevel;
    private double riskScore;
    private double patternScore; // PRODUCTION FIX: Alias for riskScore
    
    // Pattern characteristics
    private String patternType;
    private boolean hasSequentialNumbers;
    private boolean isSequential;
    private boolean hasRepeatingDigits;
    private boolean hasRepeatedDigits; // Alias for hasRepeatingDigits
    private boolean hasSequentialPattern; // Alias for hasSequentialNumbers
    private boolean hasCommonPattern;
    private boolean isValidChecksum;
    private boolean followsStandardFormat;
    private boolean isTestPattern;
    
    // Structure analysis
    private int digitCount;
    private int letterCount;
    private int specialCharacterCount;
    private double entropyScore;
    private String formatCompliance;
    
    // Fraud indicators
    private boolean matchesKnownFraudPattern;
    private boolean isTypicallyFaked;
    private boolean hasTestAccountPattern;
    private boolean isPlaceholderAccount;
    private List<String> suspiciousElements;
    
    // Validation results
    private boolean passesLuhnCheck;
    private boolean passesIbanCheck;
    private boolean passesRoutingCheck;
    private boolean isBankIssued;
    private String validationErrors;
    
    // Historical analysis
    private boolean hasHistoricalActivity;
    private int fraudIncidentCount;
    private LocalDateTime lastFraudIncident;
    private List<String> associatedFraudCases;
    
    // Cross-reference analysis
    private int similarAccountsFound;
    private List<String> similarAccounts;
    private boolean isPartOfPattern;
    private String patternFamily;
    
    // Analysis metadata
    private LocalDateTime analyzedAt;
    private String analysisMethod;
    private Map<String, Object> patternDetails;
    private double confidence;
    private List<String> detectionReasons;
    
    /**
     * Check if account pattern appears legitimate
     */
    public boolean isLegitimatePattern() {
        return riskLevel == PatternRiskLevel.NORMAL &&
               followsStandardFormat &&
               !matchesKnownFraudPattern;
    }

    /**
     * Check if account number is sequential
     */
    public boolean isSequential() {
        return isSequential || hasSequentialNumbers;
    }
    
    /**
     * Check if account appears to be synthetically generated
     */
    public boolean isSyntheticAccount() {
        return hasSequentialNumbers && hasRepeatingDigits && entropyScore < 0.3;
    }
    
    /**
     * Check if account requires validation
     */
    public boolean requiresValidation() {
        return !isValidChecksum || 
               riskLevel == PatternRiskLevel.MEDIUM || 
               riskLevel == PatternRiskLevel.HIGH;
    }
    
    /**
     * Check if account should be blocked
     */
    public boolean shouldBeBlocked() {
        return riskLevel == PatternRiskLevel.CRITICAL || 
               riskLevel == PatternRiskLevel.MALICIOUS ||
               matchesKnownFraudPattern;
    }
    
    /**
     * Get pattern strength score
     */
    public double getPatternStrength() {
        double strength = 0.0;
        if (followsStandardFormat) strength += 0.3;
        if (isValidChecksum) strength += 0.2;
        if (isBankIssued) strength += 0.3;
        strength += entropyScore * 0.2;
        return Math.min(strength, 1.0);
    }
    
    /**
     * Get risk mitigation recommendations
     */
    public List<String> getRiskMitigations() {
        List<String> mitigations = new java.util.ArrayList<>();
        
        if (!isValidChecksum) {
            mitigations.add("VALIDATE_ACCOUNT_CHECKSUM");
        }
        if (matchesKnownFraudPattern) {
            mitigations.add("BLOCK_TRANSACTION");
            mitigations.add("FLAG_FOR_INVESTIGATION");
        }
        if (similarAccountsFound > 5) {
            mitigations.add("ENHANCED_VERIFICATION");
        }
        if (riskLevel == PatternRiskLevel.HIGH) {
            mitigations.add("MANUAL_REVIEW");
        }
        
        return mitigations;
    }
    
    /**
     * Generate account pattern summary
     */
    public String getPatternSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Account Pattern Analysis\n");
        summary.append("Account: ").append(maskAccount(accountNumber)).append("\n");
        summary.append("Risk Level: ").append(riskLevel).append(" (").append(String.format("%.2f", riskScore)).append(")\n");
        summary.append("Pattern Type: ").append(patternType).append("\n");
        summary.append("Valid Format: ").append(followsStandardFormat).append("\n");
        
        if (matchesKnownFraudPattern) summary.append("- Matches known fraud patterns\n");
        if (isSyntheticAccount()) summary.append("- Appears synthetically generated\n");
        if (similarAccountsFound > 0) summary.append("- Similar accounts found: ").append(similarAccountsFound).append("\n");
        if (fraudIncidentCount > 0) summary.append("- Previous fraud incidents: ").append(fraudIncidentCount).append("\n");
        
        return summary.toString();
    }
    
    /**
     * Mask account number for logging/display
     */
    private String maskAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}