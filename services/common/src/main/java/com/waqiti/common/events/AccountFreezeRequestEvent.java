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
 * Account Freeze Request Event
 * 
 * This event is published when accounts need to be frozen due to compliance/security violations
 * and consumed by user/wallet services to prevent all account activity.
 * 
 * CRITICAL: This event was missing consumers, causing delayed response to compliance violations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountFreezeRequestEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Account identifiers
    private UUID userId;
    private String accountId;
    private List<String> walletIds;
    private String customerNumber;
    
    // Freeze details
    private FreezeReason freezeReason;
    private FreezeSeverity severity;
    private String freezeDescription;
    private FreezeScope freezeScope;
    
    // Compliance context
    private String complianceViolation;
    private String sanctionsListMatch;
    private String amlRuleViolated;
    private String suspiciousActivityPattern;
    private Double riskScore;
    
    // Financial impact
    private BigDecimal totalAccountBalance;
    private BigDecimal pendingTransactionAmount;
    private Integer pendingTransactionCount;
    private List<UUID> affectedTransactionIds;
    
    // Administrative details
    private String requestingSystem; // COMPLIANCE_ENGINE, FRAUD_DETECTION, MANUAL_REQUEST, etc.
    private String requestingOfficerId;
    private String caseId;
    private String investigationId;
    
    // Timing information
    private LocalDateTime requestedAt;
    private LocalDateTime effectiveFrom;
    private LocalDateTime reviewDate; // When the freeze should be reviewed
    private LocalDateTime expirationDate; // Optional - for temporary freezes
    
    // Notification requirements
    private boolean notifyCustomer;
    private boolean notifyRegulators;
    private boolean notifyLawEnforcement;
    private List<String> regulatoryBodies;
    
    // Related entities
    private List<UUID> relatedUserIds; // Other accounts to investigate
    private List<String> linkedAccountIds; // Linked accounts to freeze
    private Map<String, Object> metadata;
    
    /**
     * Freeze severity levels
     */
    public enum FreezeSeverity {
        LOW,        // Temporary restriction, can be auto-reviewed
        MEDIUM,     // Requires compliance review within 48 hours
        HIGH,       // Requires senior management review within 24 hours
        CRITICAL    // Immediate freeze with regulatory reporting
    }
    
    /**
     * Freeze reason categories
     */
    public enum FreezeReason {
        SANCTIONS_MATCH,            // OFAC/sanctions list match
        AML_VIOLATION,              // Anti-money laundering violation
        TERRORIST_FINANCING,        // Suspected terrorist financing
        FRAUD_DETECTION,            // Fraud detected on account
        COURT_ORDER,                // Legal/court order
        REGULATORY_REQUIREMENT,     // Regulatory authority requirement
        KYC_FAILURE,                // Know Your Customer verification failed
        SUSPICIOUS_ACTIVITY,        // Suspicious activity pattern detected
        INTERNAL_INVESTIGATION,     // Internal compliance investigation
        LAW_ENFORCEMENT_REQUEST,    // Law enforcement request
        TAX_COMPLIANCE,             // Tax compliance issues
        BANKRUPTCY,                 // Bankruptcy proceedings
        DECEASED_ACCOUNT_HOLDER     // Account holder deceased
    }
    
    /**
     * Freeze scope - what aspects of the account to freeze
     */
    public enum FreezeScope {
        FULL_FREEZE,           // All account activity blocked
        DEBIT_ONLY,            // Only outgoing transactions blocked
        CREDIT_ONLY,           // Only incoming transactions blocked
        INTERNATIONAL_ONLY,    // Only international transactions blocked
        HIGH_VALUE_ONLY,       // Only high-value transactions blocked
        SPECIFIC_CURRENCIES,   // Only specific currency transactions blocked
        INVESTIGATION_HOLD     // Temporary hold for investigation
    }
    
    /**
     * Check if freeze requires immediate action
     */
    public boolean requiresImmediateAction() {
        return severity == FreezeSeverity.CRITICAL || 
               severity == FreezeSeverity.HIGH ||
               freezeReason == FreezeReason.SANCTIONS_MATCH ||
               freezeReason == FreezeReason.TERRORIST_FINANCING ||
               freezeReason == FreezeReason.COURT_ORDER ||
               notifyLawEnforcement;
    }
    
    /**
     * Check if freeze is temporary
     */
    public boolean isTemporaryFreeze() {
        return expirationDate != null && expirationDate.isAfter(LocalDateTime.now());
    }
    
    /**
     * Check if regulatory notification is required
     */
    public boolean requiresRegulatoryNotification() {
        return notifyRegulators || 
               freezeReason == FreezeReason.SANCTIONS_MATCH ||
               freezeReason == FreezeReason.TERRORIST_FINANCING ||
               freezeReason == FreezeReason.AML_VIOLATION;
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