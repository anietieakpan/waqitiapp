package com.waqiti.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AML Monitoring Result Model
 * 
 * Represents the result of AML transaction monitoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLMonitoringResult {
    
    private String monitoringId;
    private UUID transactionId;
    private UUID userId;
    private LocalDateTime monitoredAt;
    
    // Transaction details
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String sourceAccount;
    private String destinationAccount;
    
    // Monitoring results
    private boolean hasViolations;
    private List<AMLRuleViolation> violations;
    private Double riskScore; // 0.0 to 1.0
    private RiskLevel riskLevel;
    
    // Patterns detected
    private List<String> suspiciousPatterns;
    private boolean structuringDetected;
    private boolean velocityViolation;
    private boolean unusualPattern;
    private boolean geographicRisk;
    
    // Thresholds
    private boolean dailyLimitExceeded;
    private boolean weeklyLimitExceeded;
    private boolean monthlyLimitExceeded;
    
    // Actions required
    private boolean requiresImmediateAction;
    private boolean requiresSARFiling;
    private boolean requiresAccountFreeze;
    private boolean requiresEnhancedDueDiligence;
    
    // Recommendations
    private String recommendedAction;
    private String complianceNotes;
    private List<String> additionalChecksRequired;
    
    /**
     * Risk level categories
     */
    public enum RiskLevel {
        LOW,        // Normal transaction
        MEDIUM,     // Requires monitoring
        HIGH,       // Requires review
        CRITICAL    // Immediate action required
    }
    
    /**
     * Check if transaction should be blocked
     */
    public boolean shouldBlockTransaction() {
        return hasViolations && (
            riskLevel == RiskLevel.CRITICAL ||
            requiresImmediateAction ||
            structuringDetected ||
            violations.stream().anyMatch(v -> v.getSeverity() == AMLRuleViolation.Severity.CRITICAL)
        );
    }
    
    /**
     * Check if SAR filing is required
     */
    public boolean requiresSARFiling() {
        return requiresSARFiling ||
               structuringDetected ||
               (riskScore != null && riskScore > 0.8) ||
               violations.stream().anyMatch(v -> v.isRequiresSAR());
    }
    
    /**
     * Get highest severity violation
     */
    public AMLRuleViolation.Severity getHighestSeverity() {
        if (violations == null || violations.isEmpty()) {
            return null;
        }
        
        return violations.stream()
            .map(AMLRuleViolation::getSeverity)
            .reduce((a, b) -> {
                if (a == AMLRuleViolation.Severity.CRITICAL || b == AMLRuleViolation.Severity.CRITICAL) {
                    return AMLRuleViolation.Severity.CRITICAL;
                }
                if (a == AMLRuleViolation.Severity.HIGH || b == AMLRuleViolation.Severity.HIGH) {
                    return AMLRuleViolation.Severity.HIGH;
                }
                if (a == AMLRuleViolation.Severity.MEDIUM || b == AMLRuleViolation.Severity.MEDIUM) {
                    return AMLRuleViolation.Severity.MEDIUM;
                }
                return AMLRuleViolation.Severity.LOW;
            })
            .orElse(AMLRuleViolation.Severity.LOW);
    }
}