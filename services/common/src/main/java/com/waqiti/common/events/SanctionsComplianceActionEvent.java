package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sanctions Compliance Action Event
 * 
 * Published when compliance actions are taken in response to sanctions violations
 * Tracks all remediation activities and regulatory responses
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Data
@Builder
public class SanctionsComplianceActionEvent {
    
    // Event identification
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    @Builder.Default
    private String eventType = "SANCTIONS_COMPLIANCE_ACTION";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
    
    // Action details
    private String actionId;
    private ComplianceActionType actionType;
    private String actionStatus; // INITIATED, IN_PROGRESS, COMPLETED, FAILED
    private LocalDateTime actionTimestamp;
    
    // Related violation
    private String violationId;
    private String screeningId;
    private String originalEventId;
    
    // Subject information
    private UUID userId;
    private UUID transactionId;
    private UUID merchantId;
    private String entityName;
    private String entityType;
    
    // Action taken
    private String actionTaken;
    private String actionReason;
    private String actionAuthorization;
    private String performedBy;
    private String authorizedBy;
    
    // Blocking details (if applicable)
    private BlockingDetails blockingDetails;
    
    // Asset freezing (if applicable)
    private AssetFreezingDetails assetDetails;
    
    // Regulatory reporting
    private RegulatoryReporting regulatoryReporting;
    
    // Timeline tracking
    private LocalDateTime detectionTime;
    private LocalDateTime actionTime;
    private LocalDateTime reportingTime;
    private String responseTime; // Duration from detection to action
    
    // Compliance validation
    private boolean complianceValidated;
    private String complianceOfficer;
    private String complianceNotes;
    private LocalDateTime complianceTimestamp;
    
    // Additional context
    private Map<String, Object> additionalData;
    private String correlationId;
    private List<String> relatedEvents;
    
    /**
     * Type of compliance action taken
     */
    public enum ComplianceActionType {
        ACCOUNT_BLOCKING,         // Account immediately blocked
        TRANSACTION_BLOCKING,     // Specific transaction blocked
        ASSET_FREEZING,          // Assets frozen per regulations
        FUNDS_SEIZURE,           // Funds seized by authorities
        SAR_FILING,              // Suspicious Activity Report filed
        LAW_ENFORCEMENT_NOTIFICATION, // Law enforcement notified
        REGULATORY_REPORTING,     // Regulatory body notified
        ENHANCED_DUE_DILIGENCE,  // EDD procedures initiated
        CUSTOMER_EXIT,           // Customer relationship terminated
        INVESTIGATION_INITIATED,  // Internal investigation started
        FALSE_POSITIVE_CLEARANCE, // Match cleared as false positive
        MANUAL_REVIEW_INITIATED,  // Manual compliance review
        SYSTEM_ALERT_GENERATED,   // Alert sent to stakeholders
        EXECUTIVE_NOTIFICATION,   // Executive team notified
        AUDIT_TRAIL_CREATED      // Comprehensive audit record created
    }
    
    /**
     * Blocking details
     */
    @Data
    @Builder
    public static class BlockingDetails {
        private String blockingOrderId;
        private String blockingType; // IMMEDIATE, DELAYED, CONDITIONAL
        private LocalDateTime blockingTimestamp;
        private String blockingReason;
        private String blockingDuration; // PERMANENT, TEMPORARY, INDEFINITE
        private LocalDateTime expectedUnblockDate;
        private List<String> blockedServices;
        private List<String> blockedTransactionTypes;
        private BigDecimal blockedAmount;
        private String blockingAuthority;
        private String legalBasis;
    }
    
    /**
     * Asset freezing details
     */
    @Data
    @Builder
    public static class AssetFreezingDetails {
        private String freezingOrderId;
        private LocalDateTime freezingTimestamp;
        private BigDecimal frozenAmount;
        private String currency;
        private List<String> frozenAccountIds;
        private String freezingAuthority;
        private String legalBasis;
        private String freezingDuration;
        private boolean assetsSeized;
        private String custodianAssigned;
        private Map<String, Object> assetInventory;
    }
    
    /**
     * Regulatory reporting details
     */
    @Data
    @Builder
    public static class RegulatoryReporting {
        private String reportId;
        private String reportType; // SAR, OFAC_REPORT, BLOCKED_PROPERTY, CTR
        private List<String> regulatoryBodies;
        private LocalDateTime reportingDeadline;
        private LocalDateTime actualReportingTime;
        private String reportStatus; // PENDING, FILED, ACKNOWLEDGED, REJECTED
        private String confirmationNumber;
        private String filedBy;
        private Map<String, String> reportDetails;
    }
    
    /**
     * Check if action was taken within regulatory timeframe
     */
    public boolean wasActionTimelyTaken() {
        if (detectionTime == null || actionTime == null) {
            return false;
        }
        
        // OFAC requires immediate action for confirmed matches
        long minutesBetween = java.time.Duration.between(detectionTime, actionTime).toMinutes();
        return minutesBetween <= 30; // 30 minutes maximum for immediate action
    }
    
    /**
     * Check if regulatory reporting requirements are met
     */
    public boolean areReportingRequirementsMet() {
        if (regulatoryReporting == null) {
            return false;
        }
        
        return "FILED".equals(regulatoryReporting.getReportStatus()) ||
               "ACKNOWLEDGED".equals(regulatoryReporting.getReportStatus());
    }
    
    /**
     * Get action severity level
     */
    public String getActionSeverity() {
        switch (actionType) {
            case FUNDS_SEIZURE:
            case LAW_ENFORCEMENT_NOTIFICATION:
                return "MAXIMUM";
            case ACCOUNT_BLOCKING:
            case ASSET_FREEZING:
                return "CRITICAL";
            case TRANSACTION_BLOCKING:
            case SAR_FILING:
                return "HIGH";
            case ENHANCED_DUE_DILIGENCE:
            case REGULATORY_REPORTING:
                return "MEDIUM";
            default:
                return "STANDARD";
        }
    }
    
    /**
     * Check if this action requires executive notification
     */
    public boolean requiresExecutiveNotification() {
        return actionType == ComplianceActionType.ACCOUNT_BLOCKING ||
               actionType == ComplianceActionType.ASSET_FREEZING ||
               actionType == ComplianceActionType.FUNDS_SEIZURE ||
               actionType == ComplianceActionType.LAW_ENFORCEMENT_NOTIFICATION ||
               (blockingDetails != null && blockingDetails.getBlockedAmount() != null &&
                blockingDetails.getBlockedAmount().compareTo(new BigDecimal("100000")) > 0);
    }
}