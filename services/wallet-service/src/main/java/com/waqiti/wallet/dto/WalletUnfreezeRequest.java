package com.waqiti.wallet.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet unfreeze request DTO
 * Used to unfreeze wallets after compliance or security issues are resolved
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletUnfreezeRequest {
    
    @NotNull(message = "Wallet ID is required")
    private UUID walletId;
    
    @NotBlank(message = "Unfreeze reason is required")
    @Size(max = 500, message = "Unfreeze reason cannot exceed 500 characters")
    private String reason;
    
    @NotBlank(message = "Resolution type is required")
    private String resolutionType; // INVESTIGATION_COMPLETED, COMPLIANCE_CLEARED, COURT_ORDER_LIFTED, ADMINISTRATIVE_DECISION
    
    @Size(max = 1000, message = "Resolution notes cannot exceed 1000 characters")
    private String resolutionNotes;
    
    // Authority and approval
    @NotBlank(message = "Authorized by field is required")
    private String authorizedBy;
    
    private String approvalReference;
    private String clearanceNumber;
    private String originalFreezeReference;
    
    // Resolution details
    private LocalDateTime effectiveDate;
    private boolean isImmediate;
    private boolean isPartialUnfreeze;
    private List<String> operationsToRestore; // WITHDRAW, DEPOSIT, TRANSFER_OUT, TRANSFER_IN, ALL
    
    // Conditions and restrictions
    private List<String> ongoingRestrictions;
    private boolean requiresEnhancedMonitoring;
    private String monitoringPeriod; // DAYS_30, DAYS_90, MONTHS_6, MONTHS_12, PERMANENT
    private Map<String, Object> transactionLimits;
    
    // Documentation and evidence
    private List<String> clearanceDocuments;
    private Map<String, Object> resolutionEvidence;
    private String investigationSummary;
    private String complianceSignOff;
    
    // Legal and regulatory
    private String legalBasis;
    private boolean requiresRegulatoryNotification;
    private List<String> regulatoryReferences;
    private boolean courtOrderRequired;
    
    // Customer notification
    private boolean notifyCustomer;
    private String customerMessage;
    private String communicationMethod; // EMAIL, SMS, MAIL, IN_APP
    private boolean includeRestrictions;
    
    // Risk assessment
    @NotNull(message = "Risk level is required")
    private String postUnfreezeRiskLevel; // LOW, MEDIUM, HIGH
    
    private String riskMitigationPlan;
    private List<String> additionalControls;
    private boolean requiresRegularReview;
    
    // System and audit
    private String requestSource; // MANUAL, AUTOMATED, API, COURT_ORDER
    private Map<String, Object> auditTrail;
    private LocalDateTime reviewDate;
    private String reviewAssignee;
    
    // Related entities and references
    private UUID relatedUserId;
    private List<UUID> relatedTransactions;
    private String originalFreezeId;
    private String caseFileNumber;
    
    // Approval workflow
    private boolean requiresManagerApproval;
    private boolean requiresComplianceApproval;
    private boolean requiresLegalApproval;
    private List<String> approvalChain;
    
    // Monitoring and follow-up
    private boolean enableAutomaticMonitoring;
    private Map<String, Object> monitoringParameters;
    private LocalDateTime nextReviewDate;
    private String followUpRequired;
    
    // Validation methods
    @AssertTrue(message = "Effective date cannot be more than 7 days in the future")
    public boolean isEffectiveDateValid() {
        if (effectiveDate == null) {
            return true; // Immediate unfreeze
        }
        return effectiveDate.isBefore(LocalDateTime.now().plusDays(7));
    }
    
    @AssertTrue(message = "Operations to restore must be specified for partial unfreeze")
    public boolean isPartialUnfreezeValid() {
        if (!isPartialUnfreeze) {
            return true;
        }
        return operationsToRestore != null && !operationsToRestore.isEmpty();
    }
    
    @AssertTrue(message = "Court order reference required for court-ordered unfreezes")
    public boolean isCourtOrderValid() {
        if (!"COURT_ORDER_LIFTED".equals(resolutionType)) {
            return true;
        }
        return approvalReference != null && !approvalReference.trim().isEmpty();
    }
    
    @AssertTrue(message = "Enhanced monitoring period required when enhanced monitoring is enabled")
    public boolean isEnhancedMonitoringValid() {
        if (!requiresEnhancedMonitoring) {
            return true;
        }
        return monitoringPeriod != null && !monitoringPeriod.trim().isEmpty();
    }
    
    // Helper methods
    public boolean isImmediate() {
        return isImmediate || effectiveDate == null || 
               effectiveDate.isBefore(LocalDateTime.now().plusMinutes(5));
    }
    
    public boolean isFullUnfreeze() {
        return !isPartialUnfreeze;
    }
    
    public boolean allowsOperation(String operation) {
        if (isFullUnfreeze()) {
            return !hasOngoingRestriction(operation);
        }
        
        if (operationsToRestore == null || operationsToRestore.isEmpty()) {
            return false;
        }
        
        if (operationsToRestore.contains("ALL")) {
            return !hasOngoingRestriction(operation);
        }
        
        return operationsToRestore.contains(operation) && !hasOngoingRestriction(operation);
    }
    
    private boolean hasOngoingRestriction(String operation) {
        return ongoingRestrictions != null && 
               (ongoingRestrictions.contains("ALL") || ongoingRestrictions.contains(operation));
    }
    
    public boolean requiresHighLevelApproval() {
        return "HIGH".equals(postUnfreezeRiskLevel) || 
               "COURT_ORDER_LIFTED".equals(resolutionType) ||
               courtOrderRequired ||
               requiresLegalApproval;
    }
    
    public int getMonitoringPeriodDays() {
        if (monitoringPeriod == null) return 0;
        
        return switch (monitoringPeriod) {
            case "DAYS_30" -> 30;
            case "DAYS_90" -> 90;
            case "MONTHS_6" -> 180;
            case "MONTHS_12" -> 365;
            case "PERMANENT" -> -1;
            default -> 0;
        };
    }
    
    public boolean hasTransactionLimits() {
        return transactionLimits != null && !transactionLimits.isEmpty();
    }
}