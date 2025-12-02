package com.waqiti.payment.domain;

import com.waqiti.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing individual cash deposit transaction events
 * Follows Waqiti architecture patterns with BaseEntity extension
 */
@Entity
@Table(name = "cash_deposit_transactions", indexes = {
    @Index(name = "idx_cash_deposit_id", columnList = "cash_deposit_id"),
    @Index(name = "idx_transaction_id", columnList = "transaction_id", unique = true),
    @Index(name = "idx_provider_reference", columnList = "provider_reference"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_settlement_id", columnList = "settlement_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CashDepositTransaction extends BaseEntity {
    
    @Column(name = "cash_deposit_id", nullable = false)
    private UUID cashDepositId;
    
    @Column(name = "transaction_id", unique = true, nullable = false, length = 100)
    private String transactionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "fee", precision = 19, scale = 2)
    private BigDecimal fee;
    
    @Column(name = "net_amount", precision = 19, scale = 2)
    private BigDecimal netAmount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "provider_reference", length = 100)
    private String providerReference;
    
    @Column(name = "provider_status", length = 50)
    private String providerStatus;
    
    @Column(name = "provider_message", columnDefinition = "TEXT")
    private String providerMessage;
    
    @Column(name = "location_id", length = 50)
    private String locationId;
    
    @Column(name = "location_name", length = 200)
    private String locationName;
    
    @Column(name = "cashier_id", length = 50)
    private String cashierId;
    
    @Column(name = "terminal_id", length = 50)
    private String terminalId;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    
    @Column(name = "reconciliation_status", length = 30)
    private String reconciliationStatus;
    
    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;
    
    @Column(name = "settlement_id", length = 100)
    private String settlementId;
    
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
    
    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;
    
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;
    
    @Override
    protected void onPrePersist() {
        super.onPrePersist();
        if (status == null) {
            status = TransactionStatus.INITIATED;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
        setBusinessKey(transactionId);
    }
    
    // Business logic methods
    public boolean canRetry() {
        return status == TransactionStatus.FAILED && 
               retryCount < maxRetries &&
               (nextRetryAt == null || LocalDateTime.now().isAfter(nextRetryAt));
    }
    
    public void incrementRetry() {
        if (retryCount == null) {
            retryCount = 0;
        }
        retryCount++;
        // Exponential backoff: 1 min, 5 min, 15 min
        int delayMinutes = (int) Math.pow(3, retryCount) * 1;
        nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
    }
    
    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
    
    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.failureReason = reason;
    }
    
    public void markProcessing() {
        this.status = TransactionStatus.PROCESSING;
        this.processedAt = LocalDateTime.now();
    }
    
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }
    
    public boolean isProcessing() {
        return status == TransactionStatus.PROCESSING;
    }
}

