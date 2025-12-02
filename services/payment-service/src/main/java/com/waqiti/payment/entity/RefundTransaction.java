package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refund Transaction Entity
 * 
 * Tracks all refund transactions with comprehensive audit trail
 * and compliance information for financial reconciliation.
 */
@Entity
@Table(name = "refund_transactions", indexes = {
    @Index(name = "idx_refund_payment_id", columnList = "originalPaymentId"),
    @Index(name = "idx_refund_status", columnList = "status"),
    @Index(name = "idx_refund_provider_id", columnList = "providerRefundId"),
    @Index(name = "idx_refund_created_at", columnList = "createdAt"),
    @Index(name = "idx_refund_correlation_id", columnList = "correlationId"),
    @Index(name = "idx_refund_user_id", columnList = "requestedBy")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents lost updates in concurrent refund processing
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Primary identifiers
    @Column(name = "refund_id", unique = true, nullable = false)
    private String refundId;

    @Column(name = "original_payment_id", nullable = false)
    private String originalPaymentId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "correlation_id")
    private String correlationId;

    // Status and processing
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_stage")
    private RefundProcessingStage processingStage;

    @Column(name = "status_message")
    private String statusMessage;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Financial information
    @Column(name = "requested_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal requestedAmount;

    @Column(name = "refund_amount", precision = 19, scale = 4)
    private BigDecimal refundAmount;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "net_refund_amount", precision = 19, scale = 4)
    private BigDecimal netRefundAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate", precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "base_currency", length = 3)
    private String baseCurrency;

    // Provider information
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type")
    private ProviderType providerType;

    @Column(name = "provider_refund_id")
    private String providerRefundId;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Column(name = "provider_status")
    private String providerStatus;

    @Column(name = "provider_message", columnDefinition = "TEXT")
    private String providerMessage;

    @Column(name = "provider_metadata", columnDefinition = "TEXT") // JSON
    private String providerMetadata;

    // Timing information
    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "estimated_arrival")
    private LocalDateTime estimatedArrival;

    @Column(name = "processing_time_millis")
    private Long processingTimeMillis;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    // Compliance and security
    @Column(name = "compliance_check_id")
    private String complianceCheckId;

    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_status")
    private ComplianceStatus complianceStatus;

    @Column(name = "aml_check_result")
    private String amlCheckResult;

    @Column(name = "fraud_check_result")
    private String fraudCheckResult;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "compliance_flags", columnDefinition = "TEXT") // JSON array
    private String complianceFlags;

    // Audit trail
    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "source_application")
    private String sourceApplication;

    // Financial reconciliation
    @Column(name = "ledger_entry_id")
    private String ledgerEntryId;

    @Column(name = "settlement_batch_id")
    private String settlementBatchId;

    @Column(name = "reconciliation_id")
    private String reconciliationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status")
    private ReconciliationStatus reconciliationStatus;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    // Customer information
    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_notified", nullable = false)
    private Boolean customerNotified = false;

    @Column(name = "customer_notification_sent")
    private LocalDateTime customerNotificationSent;

    // Merchant information
    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "merchant_notified", nullable = false)
    private Boolean merchantNotified = false;

    @Column(name = "merchant_notification_sent")
    private LocalDateTime merchantNotificationSent;

    // Technical metadata
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "request_source")
    private String requestSource;

    @Column(name = "api_version")
    private String apiVersion;

    @Column(name = "metadata", columnDefinition = "TEXT") // JSON
    private String metadata;

    // Batch processing
    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "batch_position")
    private Integer batchPosition;

    @Column(name = "batch_processed_at")
    private LocalDateTime batchProcessedAt;

    // Supporting documentation
    @Column(name = "support_ticket_id")
    private String supportTicketId;

    @Column(name = "dispute_id")
    private String disputeId;

    @Column(name = "chargeback_id")
    private String chargebackId;

    @Column(name = "attachment_urls", columnDefinition = "TEXT") // JSON array
    private String attachmentUrls;

    // Performance metrics
    @Column(name = "performance_metrics", columnDefinition = "TEXT") // JSON
    private String performanceMetrics;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "span_id")
    private String spanId;

    // Standard audit fields
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

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

    public enum ProviderType {
        STRIPE,
        PAYPAL,
        WISE,
        PLAID,
        INTERNAL,
        BANK_TRANSFER,
        CRYPTO
    }

    // Business Logic Methods
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

    public void markAsCompleted() {
        this.status = RefundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.processingStage = RefundProcessingStage.COMPLETED;
    }

    public void markAsFailed(String errorCode, String errorMessage) {
        this.status = RefundStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
    }

    @PrePersist
    protected void onCreate() {
        if (initiatedAt == null) {
            initiatedAt = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (customerNotified == null) {
            customerNotified = false;
        }
        if (merchantNotified == null) {
            merchantNotified = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // Calculate processing time if completed
        if (status == RefundStatus.COMPLETED && completedAt != null && initiatedAt != null) {
            this.processingTimeMillis = java.time.Duration.between(initiatedAt, completedAt).toMillis();
        }
    }
}