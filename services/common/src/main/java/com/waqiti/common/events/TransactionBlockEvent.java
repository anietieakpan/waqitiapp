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
 * Transaction Block Event
 * 
 * This event is published when transactions need to be blocked due to AML/compliance violations
 * and consumed by wallet/payment services to prevent transaction execution.
 * 
 * CRITICAL: This event was missing consumers, causing compliance violations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionBlockEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Transaction identifiers
    private UUID transactionId;
    private UUID userId;
    private String accountId;
    private String walletId;
    
    // Block details
    private BlockReason blockReason;
    private BlockSeverity severity;
    private String blockDescription;
    private List<String> complianceViolations;
    
    // Transaction details
    private BigDecimal transactionAmount;
    private String currency;
    private String transactionType;
    private String paymentMethod;
    private UUID recipientId;
    private String recipientAccountId;
    
    // Timing information
    private LocalDateTime blockedAt;
    private LocalDateTime transactionInitiatedAt;
    private LocalDateTime blockExpiresAt; // Optional - for temporary blocks
    
    // Compliance context
    private String sanctionsListMatch;
    private String amlRuleViolated;
    private Double riskScore;
    private String complianceOfficerId;
    
    // Administrative details
    private String blockingSystem; // AML_ENGINE, MANUAL_REVIEW, SANCTIONS_CHECK, etc.
    private String caseId; // For compliance case tracking
    private boolean requiresManualReview;
    private boolean notifyRegulators;
    
    // Additional context
    private Map<String, Object> metadata;
    private List<UUID> relatedTransactionIds;
    
    /**
     * Block severity levels
     */
    public enum BlockSeverity {
        LOW,        // Temporary block, can be auto-reviewed
        MEDIUM,     // Requires compliance team review
        HIGH,       // Requires senior compliance officer review
        CRITICAL    // Requires immediate regulatory reporting
    }
    
    /**
     * Block reason categories
     */
    public enum BlockReason {
        SANCTIONS_MATCH,        // OFAC/sanctions list match
        AML_VELOCITY_CHECK,     // Velocity limits exceeded
        AML_PATTERN_DETECTION,  // Suspicious transaction patterns
        KYC_INCOMPLETE,         // Customer verification incomplete
        MANUAL_REVIEW,          // Flagged for manual review
        REGULATORY_HOLD,        // Regulatory authority hold
        FRAUD_PREVENTION,       // Fraud detection triggered
        COMPLIANCE_RULE,        // General compliance rule violation
        COUNTRY_RESTRICTION,    // Geographic restrictions
        CURRENCY_CONTROL        // Currency control regulations
    }
    
    /**
     * Check if block requires immediate action
     */
    public boolean requiresImmediateAction() {
        return severity == BlockSeverity.CRITICAL || 
               severity == BlockSeverity.HIGH ||
               blockReason == BlockReason.SANCTIONS_MATCH ||
               notifyRegulators;
    }
    
    /**
     * Check if block is temporary
     */
    public boolean isTemporaryBlock() {
        return blockExpiresAt != null && blockExpiresAt.isAfter(LocalDateTime.now());
    }
    
    /**
     * Get priority level for processing
     */
    public int getPriorityLevel() {
        switch (severity) {
            case CRITICAL:
                return 1;
            case HIGH:
                return 2;
            case MEDIUM:
                return 3;
            case LOW:
            default:
                return 4;
        }
    }
}