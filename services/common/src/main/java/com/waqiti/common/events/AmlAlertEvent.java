package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AML Alert Event for Anti-Money Laundering monitoring
 * 
 * This event is published when suspicious patterns are detected
 * in financial transactions requiring compliance investigation
 * 
 * CRITICAL: This event triggers regulatory compliance workflows
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlAlertEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Alert identification
    private UUID alertId;
    private LocalDateTime alertTimestamp;
    private String alertSource; // TRANSACTION_MONITORING, PATTERN_ANALYSIS, ML_MODEL, etc.
    
    // Subject information
    private UUID userId;
    private String accountId;
    private String customerName;
    private String customerNumber;
    
    // Alert details
    private AmlAlertType alertType;
    private AmlSeverity severity;
    private String description;
    private Integer riskScore; // 1-100
    
    // Transaction context
    private UUID transactionId;
    private BigDecimal transactionAmount;
    private String currency;
    private String sourceCountry;
    private String destinationCountry;
    private LocalDateTime transactionTimestamp;
    
    // Pattern detection
    private List<String> detectedPatterns;
    private List<String> redFlags;
    private Map<String, Object> patternDetails;
    
    // Aggregation context
    private BigDecimal dailyTotal;
    private BigDecimal weeklyTotal;
    private Integer dailyTransactionCount;
    private Integer weeklyTransactionCount;
    
    // Related alerts
    private List<UUID> relatedAlertIds;
    private List<UUID> relatedTransactionIds;
    
    // Compliance requirements
    private boolean requiresInvestigation;
    private boolean requiresSarFiling;
    private boolean requiresAccountFreeze;
    private boolean requiresEnhancedDueDiligence;
    
    // Investigation details
    private String investigationPriority; // IMMEDIATE, HIGH, MEDIUM, LOW
    private LocalDateTime investigationDeadline;
    private String assignedInvestigator;
    private String caseId;
    
    // Regulatory context
    private String jurisdictionCode;
    private List<String> applicableRegulations;
    private List<String> requiredReports;
    
    // Automated actions taken
    private List<String> automatedActions;
    private boolean transactionBlocked;
    private boolean accountFrozen;
    
    // System metadata
    private String publishingService;
    private LocalDateTime publishedAt;
    private Map<String, Object> metadata;
    
    /**
     * AML Alert Types
     */
    public enum AmlAlertType {
        // Transaction-based alerts
        LARGE_CASH_TRANSACTION,
        LARGE_WIRE_TRANSFER,
        RAPID_MOVEMENT,
        HIGH_VELOCITY,
        
        // Pattern-based alerts
        STRUCTURING,              // Breaking large amounts into smaller transactions
        LAYERING,                 // Complex transaction chains
        INTEGRATION,              // Final stage of money laundering
        CIRCULAR_TRANSACTION,     // A -> B -> A patterns
        
        // Threshold-based alerts
        DAILY_THRESHOLD_EXCEEDED,
        WEEKLY_THRESHOLD_EXCEEDED,
        MONTHLY_THRESHOLD_EXCEEDED,
        
        // Geography-based alerts
        HIGH_RISK_JURISDICTION,
        SANCTIONED_COUNTRY,
        TAX_HAVEN_TRANSACTION,
        
        // Behavior-based alerts
        UNUSUAL_PATTERN,
        OFF_HOURS_TRANSACTION,
        DORMANT_ACCOUNT_ACTIVITY,
        SUDDEN_ACTIVITY_SPIKE,
        
        // Entity-based alerts
        PEP_TRANSACTION,          // Politically Exposed Person
        WATCHLIST_MATCH,
        ADVERSE_MEDIA,
        
        // Compliance alerts
        MISSING_KYC,
        EXPIRED_KYC,
        INCOMPLETE_INFORMATION,
        
        // System alerts
        MANUAL_ALERT,
        ML_MODEL_ALERT,
        RULE_ENGINE_ALERT,
        SYSTEM_ERROR
    }
    
    /**
     * AML Alert Severity Levels
     */
    public enum AmlSeverity {
        CRITICAL,  // Immediate action required, potential criminal activity
        HIGH,      // Significant risk, requires investigation within 24h
        MEDIUM,    // Moderate risk, requires investigation within 72h
        LOW,       // Low risk, routine review
        INFO       // Informational only
    }
    
    /**
     * Transaction detail for investigation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDetail implements Serializable {
        private UUID transactionId;
        private LocalDateTime timestamp;
        private BigDecimal amount;
        private String currency;
        private String type;
        private String fromAccount;
        private String toAccount;
        private String fromName;
        private String toName;
        private String fromCountry;
        private String toCountry;
        private Map<String, Object> additionalInfo;
    }
    
    /**
     * Pattern match detail
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternMatch implements Serializable {
        private String patternType;
        private Double confidenceScore;
        private String description;
        private LocalDateTime detectedAt;
        private Map<String, Object> evidence;
    }
    
    /**
     * Check if immediate action is required
     */
    public boolean requiresImmediateAction() {
        return severity == AmlSeverity.CRITICAL ||
               alertType == AmlAlertType.STRUCTURING ||
               alertType == AmlAlertType.SANCTIONED_COUNTRY ||
               alertType == AmlAlertType.PEP_TRANSACTION ||
               requiresSarFiling ||
               requiresAccountFreeze;
    }
    
    /**
     * Get investigation priority based on severity and type
     */
    public String getInvestigationPriority() {
        if (investigationPriority != null) {
            return investigationPriority;
        }
        
        if (severity == AmlSeverity.CRITICAL) {
            return "IMMEDIATE";
        } else if (severity == AmlSeverity.HIGH) {
            return "HIGH";
        } else if (severity == AmlSeverity.MEDIUM) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    /**
     * Get investigation deadline based on priority
     */
    public LocalDateTime getInvestigationDeadline() {
        if (investigationDeadline != null) {
            return investigationDeadline;
        }
        
        LocalDateTime now = LocalDateTime.now();
        switch (getInvestigationPriority()) {
            case "IMMEDIATE":
                return now.plusHours(4);
            case "HIGH":
                return now.plusHours(24);
            case "MEDIUM":
                return now.plusHours(72);
            case "LOW":
            default:
                return now.plusDays(7);
        }
    }
}