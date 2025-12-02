package com.waqiti.recurringpayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Entity
@Table(name = "recurring_executions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "recurring_payment_id", nullable = false)
    private String recurringPaymentId;
    
    @Column(name = "scheduled_date", nullable = false)
    private Instant scheduledDate;
    
    @Column(name = "executed_at")
    private Instant executedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "failed_at")
    private Instant failedAt;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExecutionStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private ExecutionTrigger trigger;
    
    @Column(name = "payment_id")
    private String paymentId;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @Column(name = "failure_code")
    private String failureCode;
    
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;
    
    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;
    
    @Column(name = "retry_at")
    private Instant retryAt;
    
    @Column(name = "next_retry_delay_minutes")
    private Integer nextRetryDelayMinutes;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recurring_execution_metadata", 
                    joinColumns = @JoinColumn(name = "execution_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;
    
    @Version
    private Long version;
    
    // Business logic methods
    
    public boolean isSuccessful() {
        return status == ExecutionStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }
    
    public boolean canRetry() {
        return isFailed() && attemptCount < maxAttempts && retryAt != null;
    }
    
    public boolean isRetryDue() {
        return canRetry() && Instant.now().isAfter(retryAt);
    }
    
    public void markAsProcessing() {
        this.status = ExecutionStatus.PROCESSING;
        this.executedAt = Instant.now();
        this.attemptCount++;
    }
    
    public void markAsCompleted(String paymentId) {
        this.status = ExecutionStatus.COMPLETED;
        this.paymentId = paymentId;
        this.completedAt = Instant.now();
        this.processingTimeMs = executedAt != null ? 
            completedAt.toEpochMilli() - executedAt.toEpochMilli() : null;
    }
    
    public void markAsFailed(String reason, String code) {
        this.status = ExecutionStatus.FAILED;
        this.failureReason = reason;
        this.failureCode = code;
        this.failedAt = Instant.now();
        this.processingTimeMs = executedAt != null ? 
            failedAt.toEpochMilli() - executedAt.toEpochMilli() : null;
    }
    
    public void scheduleRetry(int delayMinutes) {
        this.retryAt = Instant.now().plusSeconds(delayMinutes * 60L);
        this.nextRetryDelayMinutes = delayMinutes;
        this.retryCount++;
        this.status = ExecutionStatus.RETRYING;
    }
    
    public int calculateNextRetryDelay() {
        // Exponential backoff: 15, 30, 60, 120 minutes
        return Math.min(15 * (int) Math.pow(2, retryCount), 120);
    }
}