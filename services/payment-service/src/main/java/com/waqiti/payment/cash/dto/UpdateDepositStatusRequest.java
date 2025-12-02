package com.waqiti.payment.cash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-Grade Update Deposit Status Request DTO
 * 
 * Comprehensive request for updating cash deposit status with full audit trail:
 * - Status transition validation and business rules
 * - Detailed reason codes and error handling
 * - Compliance and regulatory reporting
 * - Integration with external systems
 * - Audit trail and change tracking
 * - Automated notification triggers
 * 
 * @author Waqiti Cash Management Team  
 * @version 2.1.0
 * @since 2025-01-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDepositStatusRequest {
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotBlank(message = "Current status is required")
    @Pattern(regexp = "PENDING|PROCESSING|COMPLETED|FAILED|CANCELLED|DISPUTED|REFUNDED", 
             message = "Invalid status value")
    private String newStatus;
    
    @NotBlank(message = "Previous status is required for validation")
    @Pattern(regexp = "PENDING|PROCESSING|COMPLETED|FAILED|CANCELLED|DISPUTED|REFUNDED", 
             message = "Invalid previous status value")
    private String previousStatus;
    
    @NotBlank(message = "Reason code is required")
    private String reasonCode;
    
    private String reasonDescription;
    
    @NotBlank(message = "Updated by user ID is required")
    private String updatedBy;
    
    @NotNull(message = "Update timestamp is required")
    private LocalDateTime updatedAt;
    
    // Network and Provider Information
    private String networkId;
    private String providerId;
    private String networkTransactionId;
    private String networkStatus;
    private String networkResponseCode;
    private String networkResponseMessage;
    
    // Settlement Information
    private String settlementId;
    private String settlementStatus;
    private LocalDateTime settlementDate;
    private String settlementBatch;
    private String clearingReference;
    
    // Error and Exception Information
    private String errorCode;
    private String errorMessage;
    private String errorCategory;
    private String technicalDetails;
    private String stackTrace;
    private Integer retryCount;
    private LocalDateTime lastRetryAt;
    private LocalDateTime nextRetryAt;
    
    // Compliance and Regulatory
    private Boolean requiresManualReview;
    private String complianceStatus;
    private String complianceNotes;
    private String regualtoryReportingStatus;
    private String amlStatus;
    private String kycStatus;
    
    // Business Context
    private String businessReason;
    private String customerNotificationRequired;
    private String escalationLevel;
    private String priorityLevel;
    private String customerImpact;
    
    // System Information
    private String systemSource;
    private String applicationVersion;
    private String correlationId;
    private String sessionId;
    private String requestId;
    
    // Additional Metadata
    private Map<String, String> additionalProperties;
    private Map<String, String> tags;
    private String notes;
    
    // Validation Rules
    private Boolean bypassValidation;
    private String validationOverrideReason;
    private String approvedBy;
    
    // Notification Settings
    private Boolean notifyCustomer;
    private Boolean notifyMerchant;
    private Boolean notifySupport;
    private Boolean notifyCompliance;
    private String notificationMethod;
    private String notificationTemplate;
    
    // Audit Trail
    private String auditTrailId;
    private String changeCategory;
    private String changeImpact;
    private String approvalRequired;
    private String approvalStatus;
    private String approvedByUserId;
    private LocalDateTime approvedAt;
    
    // Integration Information
    private String webhookUrl;
    private String callbackUrl;
    private Boolean triggerDownstreamUpdates;
    private String[] affectedSystems;
    
    // Performance Tracking
    private LocalDateTime requestReceivedAt;
    private LocalDateTime processingStartedAt;
    private String performanceCategory;
    private String processingPriority;
}