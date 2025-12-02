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
 * Wallet freeze request DTO
 * Used to freeze wallets for compliance or security reasons
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletFreezeRequest {
    
    @NotNull(message = "Wallet ID is required")
    private UUID walletId;
    
    @NotBlank(message = "Freeze reason is required")
    @Size(max = 500, message = "Freeze reason cannot exceed 500 characters")
    private String reason;
    
    @NotBlank(message = "Freeze type is required")
    private String freezeType; // COMPLIANCE, SECURITY, ADMINISTRATIVE, COURT_ORDER, SUSPICIOUS_ACTIVITY
    
    @Size(max = 1000, message = "Additional notes cannot exceed 1000 characters")
    private String notes;
    
    // Authority and approval
    @NotBlank(message = "Authorized by field is required")
    private String authorizedBy;
    
    private String approvalReference;
    private String caseNumber;
    private String regulatoryReference;
    
    // Freeze scope and restrictions
    private List<String> restrictedOperations; // WITHDRAW, DEPOSIT, TRANSFER_OUT, TRANSFER_IN, ALL
    private boolean allowEmergencyAccess;
    private boolean notifyCustomer;
    
    // Timing and duration
    private LocalDateTime effectiveDate;
    private LocalDateTime expirationDate;
    private boolean isPermanent;
    
    // Severity and priority
    @NotNull(message = "Severity level is required")
    private String severityLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    private boolean isUrgent;
    private boolean requiresImmediateAction;
    
    // Legal and compliance
    private String legalBasis;
    private List<String> complianceFlags;
    private boolean requiresCourtOrder;
    private boolean requiresCustomerNotification;
    
    // Documentation and evidence
    private List<String> supportingDocuments;
    private Map<String, Object> evidenceMetadata;
    private String investigationId;
    
    // Related entities
    private UUID relatedUserId;
    private List<UUID> relatedTransactions;
    private List<UUID> relatedAccounts;
    
    // Notification and escalation
    private List<String> notificationRecipients;
    private String escalationLevel;
    private boolean requiresManagerApproval;
    private boolean requiresComplianceApproval;
    
    // System metadata
    private String requestSource; // MANUAL, AUTOMATED, API, BATCH
    private String systemTriggerId;
    private Map<String, Object> auditTrail;
    
    // Customer communication
    private String customerMessage;
    private String communicationMethod; // EMAIL, SMS, MAIL, IN_APP
    private boolean suppressNotifications;
    
    // Review and appeal process
    private boolean allowAppeal;
    private String appealInstructions;
    private LocalDateTime reviewDate;
    private String reviewAssignee;
    
    // Validation methods
    @AssertTrue(message = "Effective date cannot be in the past")
    public boolean isEffectiveDateValid() {
        if (effectiveDate == null) {
            return true; // Immediate freeze
        }
        return effectiveDate.isAfter(LocalDateTime.now().minusMinutes(5)); // Allow 5 minutes tolerance
    }
    
    @AssertTrue(message = "Expiration date must be after effective date")
    public boolean isExpirationDateValid() {
        if (isPermanent || expirationDate == null) {
            return true;
        }
        LocalDateTime effective = effectiveDate != null ? effectiveDate : LocalDateTime.now();
        return expirationDate.isAfter(effective);
    }
    
    @AssertTrue(message = "Court order reference required for court-ordered freezes")
    public boolean isCourtOrderValid() {
        if (!"COURT_ORDER".equals(freezeType)) {
            return true;
        }
        return approvalReference != null && !approvalReference.trim().isEmpty();
    }
    
    // Helper methods
    public boolean isImmediate() {
        return effectiveDate == null || effectiveDate.isBefore(LocalDateTime.now().plusMinutes(5));
    }
    
    public boolean isTemporary() {
        return !isPermanent && expirationDate != null;
    }
    
    public boolean allowsOperation(String operation) {
        if (restrictedOperations == null || restrictedOperations.isEmpty()) {
            return false; // Default to blocking all operations
        }
        
        if (restrictedOperations.contains("ALL")) {
            return false;
        }
        
        return !restrictedOperations.contains(operation);
    }
    
    public boolean requiresHighLevelApproval() {
        return "CRITICAL".equals(severityLevel) || 
               "COURT_ORDER".equals(freezeType) || 
               requiresCourtOrder ||
               isPermanent;
    }
    
    public long getFreezeDurationHours() {
        if (isPermanent || expirationDate == null) {
            return -1; // Permanent
        }
        
        LocalDateTime start = effectiveDate != null ? effectiveDate : LocalDateTime.now();
        return java.time.Duration.between(start, expirationDate).toHours();
    }
}