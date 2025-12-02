package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Scheduled Payment Execution Entity - PRODUCTION READY
 *
 * Records each execution attempt of a scheduled payment
 * Provides audit trail and execution history
 */
@Entity
@Table(name = "scheduled_payment_executions", indexes = {
    @Index(name = "idx_spe_scheduled_payment_id", columnList = "scheduled_payment_id"),
    @Index(name = "idx_spe_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_spe_status", columnList = "status"),
    @Index(name = "idx_spe_execution_date", columnList = "execution_date")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ScheduledPaymentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_payment_id", nullable = false)
    private ScheduledPayment scheduledPayment;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduledPaymentExecutionStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(nullable = false, name = "execution_date")
    private LocalDateTime executionDate;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    /**
     * Idempotency support
     */
    @Column(name = "idempotency_key", length = 255, unique = true)
    private String idempotencyKey;

    // Audit fields
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * Creates a new successful execution with idempotency support
     */
    public static ScheduledPaymentExecution create(ScheduledPayment scheduledPayment,
                                                UUID transactionId, BigDecimal amount,
                                                String currency, String idempotencyKey) {
        return ScheduledPaymentExecution.builder()
            .scheduledPayment(scheduledPayment)
            .transactionId(transactionId)
            .amount(amount)
            .currency(currency)
            .status(ScheduledPaymentExecutionStatus.COMPLETED)
            .executionDate(LocalDateTime.now())
            .idempotencyKey(idempotencyKey)
            .retryCount(0)
            .build();
    }

    /**
     * Backward compatibility
     */
    public static ScheduledPaymentExecution create(ScheduledPayment scheduledPayment,
                                                UUID transactionId, BigDecimal amount, String currency) {
        return create(scheduledPayment, transactionId, amount, currency, null);
    }

    /**
     * Creates a new failed execution with detailed error info
     */
    public static ScheduledPaymentExecution createFailed(ScheduledPayment scheduledPayment,
                                                      BigDecimal amount, String currency,
                                                      String errorMessage, String errorCode,
                                                      String idempotencyKey, Integer retryCount) {
        return ScheduledPaymentExecution.builder()
            .scheduledPayment(scheduledPayment)
            .amount(amount)
            .currency(currency)
            .status(ScheduledPaymentExecutionStatus.FAILED)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .executionDate(LocalDateTime.now())
            .idempotencyKey(idempotencyKey)
            .retryCount(retryCount)
            .build();
    }

    /**
     * Backward compatibility
     */
    public static ScheduledPaymentExecution createFailed(ScheduledPayment scheduledPayment,
                                                      BigDecimal amount, String currency, String errorMessage) {
        return createFailed(scheduledPayment, amount, currency, errorMessage, null, null, 0);
    }

    /**
     * Creates a new execution with success result and processing time
     */
    public static ScheduledPaymentExecution success(ScheduledPayment payment, UUID transactionId,
                                                   String idempotencyKey, long processingMs) {
        return ScheduledPaymentExecution.builder()
            .scheduledPayment(payment)
            .transactionId(transactionId)
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(ScheduledPaymentExecutionStatus.COMPLETED)
            .executionDate(LocalDateTime.now())
            .idempotencyKey(idempotencyKey)
            .processingDurationMs(processingMs)
            .retryCount(0)
            .build();
    }

    /**
     * Creates a new execution with failure result
     */
    public static ScheduledPaymentExecution failure(ScheduledPayment payment, String errorMessage,
                                                   String errorCode, String idempotencyKey,
                                                   Integer retryCount) {
        return ScheduledPaymentExecution.builder()
            .scheduledPayment(payment)
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(ScheduledPaymentExecutionStatus.FAILED)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .executionDate(LocalDateTime.now())
            .idempotencyKey(idempotencyKey)
            .retryCount(retryCount)
            .build();
    }

    /**
     * Business logic methods
     */
    public boolean isSuccess() {
        return ScheduledPaymentExecutionStatus.COMPLETED.equals(status);
    }

    public boolean isFailed() {
        return ScheduledPaymentExecutionStatus.FAILED.equals(status);
    }

    public boolean canRetry() {
        return isFailed() && (retryCount == null || retryCount < 3);
    }

    @PrePersist
    protected void onCreate() {
        if (executionDate == null) {
            executionDate = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}