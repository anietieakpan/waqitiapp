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
 * SAR (Suspicious Activity Report) Filing Request Event
 * 
 * This event is published when suspicious activity requires regulatory reporting
 * and consumed by compliance services to file mandatory reports.
 * 
 * CRITICAL: This event was missing consumers, causing regulatory compliance failures
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SarFilingRequestEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Subject information
    private UUID userId;
    private String accountId;
    private String customerName;
    private String customerNumber;
    private String taxId;
    
    // SAR details
    private SarCategory category;
    private SarPriority priority;
    private String violationType;
    private String suspiciousActivity;
    private String narrativeDescription;
    
    // Financial information
    private BigDecimal totalSuspiciousAmount;
    private String currency;
    private Integer transactionCount;
    private LocalDateTime activityStartDate;
    private LocalDateTime activityEndDate;
    private BigDecimal accountBalance;
    
    // Detection information
    private String detectionMethod; // AML_RULE, MANUAL_REVIEW, PATTERN_ANALYSIS, etc.
    private String detectionRuleId;
    private Double riskScore;
    private List<String> redFlags;
    private List<String> suspiciousPatterns;
    
    // Transaction details
    private List<UUID> suspiciousTransactionIds;
    private List<TransactionDetail> transactionDetails;
    
    // Related parties
    private List<RelatedParty> relatedParties;
    private List<UUID> linkedAccountIds;
    
    // Compliance context
    private String caseId;
    private String investigationId;
    private String complianceOfficerId;
    private String reviewNotes;
    
    // Filing requirements
    private LocalDateTime filingDeadline; // Regulatory deadline for filing
    private List<String> regulatoryBodies; // Which regulators to notify
    private boolean requiresImmediateFiling;
    private boolean requiresLawEnforcementNotification;
    
    // Administrative details
    private String requestingSystem;
    private LocalDateTime requestedAt;
    private Map<String, Object> metadata;
    
    /**
     * SAR category types
     */
    public enum SarCategory {
        MONEY_LAUNDERING,
        TERRORIST_FINANCING,
        STRUCTURING,            // Avoiding reporting thresholds
        SANCTIONS_VIOLATION,
        FRAUD,
        IDENTITY_THEFT,
        INSIDER_TRADING,
        MARKET_MANIPULATION,
        BRIBERY_CORRUPTION,
        TAX_EVASION,
        HUMAN_TRAFFICKING,
        ELDER_FINANCIAL_ABUSE,
        CYBER_CRIME,
        OTHER_SUSPICIOUS_ACTIVITY
    }
    
    /**
     * SAR filing priority
     */
    public enum SarPriority {
        IMMEDIATE,    // File within 24 hours
        URGENT,       // File within 48 hours
        HIGH,         // File within 5 days
        STANDARD,     // File within 30 days
        LOW           // File within 60 days
    }
    
    /**
     * Transaction detail for SAR
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDetail implements Serializable {
        private UUID transactionId;
        private LocalDateTime transactionDate;
        private BigDecimal amount;
        private String currency;
        private String transactionType;
        private String fromAccount;
        private String toAccount;
        private String fromInstitution;
        private String toInstitution;
        private String fromCountry;
        private String toCountry;
        private String suspiciousIndicator;
        private String notes;
    }
    
    /**
     * Related party information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedParty implements Serializable {
        private String partyId;
        private String partyName;
        private String partyType; // INDIVIDUAL, BUSINESS, ORGANIZATION
        private String relationship; // SENDER, RECEIVER, BENEFICIARY, etc.
        private String country;
        private String address;
        private String identificationNumber;
        private String identificationtype;
        private boolean isPEP; // Politically Exposed Person
        private boolean isSanctioned;
        private String riskLevel;
    }
    
    /**
     * Check if SAR requires immediate filing
     */
    public boolean requiresImmediateFiling() {
        return priority == SarPriority.IMMEDIATE ||
               category == SarCategory.TERRORIST_FINANCING ||
               category == SarCategory.SANCTIONS_VIOLATION ||
               requiresLawEnforcementNotification ||
               (filingDeadline != null && filingDeadline.isBefore(LocalDateTime.now().plusDays(1)));
    }
    
    /**
     * Get filing deadline based on priority
     */
    public LocalDateTime getFilingDeadline() {
        if (filingDeadline != null) {
            return filingDeadline;
        }
        
        LocalDateTime now = LocalDateTime.now();
        switch (priority) {
            case IMMEDIATE:
                return now.plusHours(24);
            case URGENT:
                return now.plusDays(2);
            case HIGH:
                return now.plusDays(5);
            case STANDARD:
                return now.plusDays(30);
            case LOW:
            default:
                return now.plusDays(60);
        }
    }
    
    /**
     * Check if law enforcement notification is required
     */
    public boolean requiresLawEnforcementNotification() {
        return requiresLawEnforcementNotification ||
               category == SarCategory.TERRORIST_FINANCING ||
               category == SarCategory.HUMAN_TRAFFICKING ||
               (totalSuspiciousAmount != null && totalSuspiciousAmount.compareTo(new BigDecimal("100000")) > 0);
    }
}