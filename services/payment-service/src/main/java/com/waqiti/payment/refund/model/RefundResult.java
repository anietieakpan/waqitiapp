package com.waqiti.payment.refund.model;

import com.waqiti.payment.core.model.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Refund Result Model
 * 
 * Comprehensive refund processing result with support for:
 * - Multi-provider refund tracking
 * - Compliance and audit information
 * - Financial reconciliation data
 * - Error handling and retry mechanisms
 * - Performance metrics and monitoring
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResult {
    
    // Primary identifiers
    private String refundId;
    private String originalPaymentId;
    private String transactionId;
    private String correlationId;
    
    // Status and processing
    private RefundStatus status;
    private RefundProcessingStage processingStage;
    private String statusMessage;
    private String errorCode;
    private String errorMessage;
    private List<String> warningMessages;
    
    // Financial information
    private BigDecimal requestedAmount;
    private BigDecimal refundAmount;
    private BigDecimal feeAmount;
    private BigDecimal netRefundAmount;
    private String currency;
    private BigDecimal exchangeRate;
    private String baseCurrency;
    
    // Provider information
    private ProviderType providerType;
    private String providerRefundId;
    private String providerTransactionId;
    private String providerStatus;
    private String providerMessage;
    private Map<String, Object> providerMetadata;
    
    // Timing information
    private Instant initiatedAt;
    private Instant processedAt;
    private Instant completedAt;
    private Instant estimatedArrival;
    private Long processingTimeMillis;
    private Integer retryCount;
    private Instant nextRetryAt;
    
    // Compliance and security
    private String complianceCheckId;
    private ComplianceStatus complianceStatus;
    private String amlCheckResult;
    private String fraudCheckResult;
    private Integer riskScore;
    private List<String> complianceFlags;
    
    // Audit trail
    private String requestedBy;
    private String approvedBy;
    private String processedBy;
    private String ipAddress;
    private String userAgent;
    private String sourceApplication;
    
    // Financial reconciliation
    private String ledgerEntryId;
    private String settlementBatchId;
    private String reconciliationId;
    private ReconciliationStatus reconciliationStatus;
    private Instant settlementDate;
    
    // Customer information
    private String customerEmail;
    private String customerPhone;
    private boolean customerNotified;
    private Instant customerNotificationSent;
    
    // Merchant information
    private String merchantId;
    private String merchantName;
    private boolean merchantNotified;
    private Instant merchantNotificationSent;
    
    // Technical metadata
    private String idempotencyKey;
    private String requestSource;
    private String apiVersion;
    private Map<String, Object> metadata;
    
    // Batch processing
    private String batchId;
    private Integer batchPosition;
    private Instant batchProcessedAt;
    
    // Supporting documentation
    private String supportTicketId;
    private String disputeId;
    private String chargebackId;
    private List<String> attachmentUrls;
    
    // Performance metrics
    private Map<String, Long> performanceMetrics;
    private String traceId;
    private String spanId;
    
    // Enums
    public enum RefundStatus {
        PENDING,
        APPROVED,
        PROCESSING,
        COMPLETED,
        FAILED,
        REJECTED,
        CANCELLED,
        EXPIRED,
        REVERSED,
        PARTIAL_SUCCESS,
        REQUIRES_MANUAL_REVIEW
    }
    
    public enum RefundProcessingStage {
        INITIATED,
        VALIDATION,
        COMPLIANCE_CHECK,
        FRAUD_CHECK,
        APPROVAL,
        PROVIDER_PROCESSING,
        LEDGER_UPDATE,
        WALLET_UPDATE,
        NOTIFICATION,
        AUDIT,
        COMPLETED
    }
    
    public enum ComplianceStatus {
        PENDING,
        PASSED,
        FAILED,
        MANUAL_REVIEW,
        EXEMPTED,
        WARNING
    }
    
    public enum ReconciliationStatus {
        PENDING,
        MATCHED,
        DISCREPANCY,
        RESOLVED,
        FAILED
    }
    
    // Helper methods
    public boolean isSuccessful() {
        return status == RefundStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == RefundStatus.FAILED || status == RefundStatus.REJECTED;
    }
    
    public boolean isPending() {
        return status == RefundStatus.PENDING || status == RefundStatus.PROCESSING;
    }
    
    public boolean requiresManualReview() {
        return status == RefundStatus.REQUIRES_MANUAL_REVIEW ||
               complianceStatus == ComplianceStatus.MANUAL_REVIEW;
    }
    
    public boolean canRetry() {
        return status == RefundStatus.FAILED && (retryCount == null || retryCount < 3);
    }
    
    public BigDecimal getEffectiveRefundAmount() {
        return netRefundAmount != null ? netRefundAmount : refundAmount;
    }
    
    public boolean hasWarnings() {
        return warningMessages != null && !warningMessages.isEmpty();
    }
    
    public boolean isPartialSuccess() {
        return status == RefundStatus.PARTIAL_SUCCESS;
    }
    
    public Long getProcessingDurationMillis() {
        if (initiatedAt != null && completedAt != null) {
            return completedAt.toEpochMilli() - initiatedAt.toEpochMilli();
        }
        return processingTimeMillis;
    }
    
    // Static factory methods
    public static RefundResult success(String refundId, BigDecimal refundAmount, String providerRefundId) {
        return RefundResult.builder()
            .refundId(refundId)
            .status(RefundStatus.COMPLETED)
            .refundAmount(refundAmount)
            .netRefundAmount(refundAmount)
            .providerRefundId(providerRefundId)
            .completedAt(Instant.now())
            .build();
    }
    
    public static RefundResult pending(String refundId, BigDecimal requestedAmount) {
        return RefundResult.builder()
            .refundId(refundId)
            .status(RefundStatus.PENDING)
            .requestedAmount(requestedAmount)
            .initiatedAt(Instant.now())
            .build();
    }
    
    public static RefundResult failed(String refundId, String errorMessage) {
        return RefundResult.builder()
            .refundId(refundId)
            .status(RefundStatus.FAILED)
            .errorMessage(errorMessage)
            .processedAt(Instant.now())
            .build();
    }
    
    public static RefundResult rejected(String refundId, String reason) {
        return RefundResult.builder()
            .refundId(refundId)
            .status(RefundStatus.REJECTED)
            .statusMessage(reason)
            .processedAt(Instant.now())
            .build();
    }
    
    public static RefundResult fraudBlocked(String refundId, List<String> riskFactors) {
        return RefundResult.builder()
            .refundId(refundId)
            .status(RefundStatus.REJECTED)
            .statusMessage("Blocked due to fraud risk")
            .errorCode("FRAUD_DETECTED")
            .complianceFlags(riskFactors)
            .fraudCheckResult("BLOCKED")
            .processedAt(Instant.now())
            .build();
    }
    
    public static RefundResult requiresManualReview(String refundId, String reason) {
        return RefundResult.builder()
            .refundId(refundId)
            .status(RefundStatus.REQUIRES_MANUAL_REVIEW)
            .statusMessage(reason)
            .complianceStatus(ComplianceStatus.MANUAL_REVIEW)
            .processedAt(Instant.now())
            .build();
    }
    
    // Builder customization
    public static class RefundResultBuilder {
        
        public RefundResultBuilder withProvider(ProviderType providerType, String providerRefundId) {
            this.providerType = providerType;
            this.providerRefundId = providerRefundId;
            return this;
        }
        
        public RefundResultBuilder withTiming(Instant initiatedAt, Instant completedAt) {
            this.initiatedAt = initiatedAt;
            this.completedAt = completedAt;
            if (initiatedAt != null && completedAt != null) {
                this.processingTimeMillis = completedAt.toEpochMilli() - initiatedAt.toEpochMilli();
            }
            return this;
        }
        
        public RefundResultBuilder withCompliance(String complianceCheckId, ComplianceStatus status) {
            this.complianceCheckId = complianceCheckId;
            this.complianceStatus = status;
            return this;
        }
        
        public RefundResultBuilder withFinancials(BigDecimal refundAmount, BigDecimal feeAmount) {
            this.refundAmount = refundAmount;
            this.feeAmount = feeAmount;
            if (refundAmount != null && feeAmount != null) {
                this.netRefundAmount = refundAmount.subtract(feeAmount);
            } else {
                this.netRefundAmount = refundAmount;
            }
            return this;
        }
        
        public RefundResultBuilder withError(String errorCode, String errorMessage) {
            this.status = RefundStatus.FAILED;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            return this;
        }
        
        public RefundResultBuilder withAudit(String requestedBy, String ipAddress) {
            this.requestedBy = requestedBy;
            this.ipAddress = ipAddress;
            return this;
        }
        
        public RefundResultBuilder withTracing(String correlationId, String traceId) {
            this.correlationId = correlationId;
            this.traceId = traceId;
            return this;
        }
    }
}