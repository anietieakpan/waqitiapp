package com.waqiti.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AML Rule Violation Model
 * 
 * Represents a specific AML rule that has been violated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLRuleViolation {
    
    private String violationId;
    private String ruleId;
    private String ruleName;
    private RuleType ruleType;
    private Severity severity;
    
    // Violation details
    private String description;
    private String violationReason;
    private BigDecimal violationAmount;
    private BigDecimal threshold;
    
    // Context
    private LocalDateTime detectedAt;
    private String detectionMethod;
    private Double confidenceScore; // 0.0 to 1.0
    
    // Required actions
    private boolean requiresImmediateAction;
    private boolean requiresSAR;
    private boolean requiresAccountReview;
    private boolean requiresTransactionBlock;
    
    // Additional information
    private String evidenceDetails;
    private String regulatoryReference; // e.g., "31 CFR 1020.320"
    
    /**
     * AML rule types
     */
    public enum RuleType {
        STRUCTURING,                // Breaking up transactions to avoid reporting
        VELOCITY,                   // Too many transactions in time period
        CUMULATIVE_THRESHOLD,       // Exceeds cumulative limits
        RAPID_MOVEMENT,            // Funds moved too quickly
        ROUND_AMOUNT,              // Suspicious round amounts
        DORMANT_REACTIVATION,      // Dormant account suddenly active
        PATTERN_ANOMALY,           // Unusual transaction pattern
        GEOGRAPHIC_RISK,          // High-risk geography
        COUNTERPARTY_RISK,         // High-risk counterparty
        TIME_PATTERN,              // Suspicious timing patterns
        CURRENCY_MIXING,           // Multiple currency transactions
        THIRD_PARTY_TRANSFER       // Suspicious third-party transfers
    }
    
    /**
     * Violation severity levels
     */
    public enum Severity {
        LOW,        // Minor violation, monitoring only
        MEDIUM,     // Requires review
        HIGH,       // Requires immediate review
        CRITICAL    // Immediate action and reporting required
    }
    
    /**
     * Check if this is a critical violation
     */
    public boolean isCritical() {
        return severity == Severity.CRITICAL || 
               ruleType == RuleType.STRUCTURING ||
               requiresImmediateAction;
    }
    
    /**
     * Get regulatory filing requirement
     */
    public String getRegulatoryFilingRequirement() {
        switch (ruleType) {
            case STRUCTURING:
                return "SAR required within 30 days per 31 CFR 1020.320";
            case CUMULATIVE_THRESHOLD:
                if (violationAmount != null && violationAmount.compareTo(new BigDecimal("10000")) > 0) {
                    return "CTR required for transactions over $10,000";
                }
                break;
            case GEOGRAPHIC_RISK:
                return "Enhanced due diligence required per 31 CFR 1010.610";
            default:
                break;
        }
        return null;
    }
}