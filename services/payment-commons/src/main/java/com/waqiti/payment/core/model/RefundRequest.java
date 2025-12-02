package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Refund Request Model
 * 
 * Comprehensive refund request with support for partial refunds,
 * compliance tracking, and multi-provider handling.
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    
    @NotNull(message = "Refund ID is required")
    @Builder.Default
    private String refundId = UUID.randomUUID().toString();
    
    @NotNull(message = "Original payment ID is required")
    private String originalPaymentId;
    
    @NotNull(message = "User ID is required")
    private String userId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    private String currency;
    
    @NotNull(message = "Reason is required")
    @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
    private String reason;
    
    private RefundType refundType;
    private RefundStatus status;
    private String transactionId;
    private String providerRefundId;
    private ProviderType providerType;
    
    // Additional enterprise fields
    private String approvedBy;
    private Instant approvedAt;
    private String rejectedBy;
    private Instant rejectedAt;
    private String rejectionReason;
    private String merchantId;
    private String merchantName;
    private String customerEmail;
    private String customerPhone;
    
    // Processing details
    private String requestedBy;
    private LocalDateTime requestedAt;
    private Instant processedAt;
    private Instant completedAt;
    private Long processingTimeMillis;
    
    // Financial details
    private BigDecimal originalAmount;
    private BigDecimal refundedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal refundFee;
    private BigDecimal netRefundAmount;
    
    // Compliance and audit
    private String idempotencyKey;
    private String complianceCheckId;
    private ComplianceStatus complianceStatus;
    private String amlCheckResult;
    private String fraudCheckResult;
    private Integer riskScore;
    
    // Metadata
    private Map<String, Object> metadata;
    private String sourceApplication;
    private String ipAddress;
    private String userAgent;
    private String deviceFingerprint;
    
    // Notification settings
    private boolean notifyCustomer;
    private boolean notifyMerchant;
    private String webhookUrl;
    
    // Retry information
    private Integer retryCount;
    private Instant nextRetryAt;
    private String lastErrorMessage;
    private String lastErrorCode;
    
    // Batch processing
    private String batchId;
    private Integer batchPosition;
    
    // Priority and SLA
    private Priority priority;
    private Instant slaDeadline;
    private boolean urgentProcessing;
    
    // Supporting documentation
    private String supportTicketId;
    private String disputeId;
    private String chargebackId;
    private String attachmentUrl;
    
    // Enums
    public enum RefundType {
        FULL, PARTIAL, CREDIT, CHARGEBACK, DISPUTE,
        CANCELLATION, ERROR_CORRECTION, GOODWILL, REGULATORY
    }
    
    public enum RefundStatus {
        PENDING, APPROVED, PROCESSING, COMPLETED, FAILED,
        REJECTED, CANCELLED, EXPIRED, REVERSED
    }
    
    public enum ComplianceStatus {
        PENDING, PASSED, FAILED, MANUAL_REVIEW, EXEMPTED
    }
    
    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }
    
    // Helper methods
    public boolean isFullRefund() {
        return refundType == RefundType.FULL || 
               (originalAmount != null && amount.compareTo(originalAmount) == 0);
    }
    
    public boolean isPartialRefund() {
        return refundType == RefundType.PARTIAL || 
               (originalAmount != null && amount.compareTo(originalAmount) < 0);
    }
    
    public boolean requiresApproval() {
        return amount.compareTo(new BigDecimal("1000")) > 0 || 
               refundType == RefundType.CHARGEBACK ||
               refundType == RefundType.DISPUTE;
    }
    
    public boolean canRetry() {
        return status == RefundStatus.FAILED && 
               (retryCount == null || retryCount < 3);
    }
    
    public BigDecimal calculateRemainingAmount() {
        if (originalAmount != null && refundedAmount != null) {
            return originalAmount.subtract(refundedAmount);
        }
        return BigDecimal.ZERO;
    }
    
    // Validation
    public void validate() {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        
        if (originalAmount != null && amount.compareTo(originalAmount) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed original amount");
        }
        
        if (refundType == RefundType.FULL && originalAmount != null && 
            amount.compareTo(originalAmount) != 0) {
            throw new IllegalArgumentException("Full refund amount must equal original amount");
        }
    }
    
    // Static factory method
    public static RefundRequest create(String originalPaymentId, BigDecimal amount, String reason) {
        return RefundRequest.builder()
            .refundId(UUID.randomUUID().toString())
            .originalPaymentId(originalPaymentId)
            .amount(amount)
            .reason(reason)
            .requestedAt(LocalDateTime.now())
            .status(RefundStatus.PENDING)
            .build();
    }
}