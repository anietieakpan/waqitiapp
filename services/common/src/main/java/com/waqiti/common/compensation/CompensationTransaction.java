package com.waqiti.common.compensation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a compensation transaction for rollback/recovery operations.
 * Used when a distributed transaction fails and needs to be reversed.
 *
 * Part of SAGA pattern implementation for microservices orchestration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensationTransaction {

    /**
     * Unique identifier for this compensation transaction
     */
    private String compensationId;

    /**
     * ID of the original transaction being compensated
     */
    private String originalTransactionId;

    /**
     * Type of compensation action to perform
     */
    private CompensationType type;

    /**
     * Current status of the compensation
     */
    private CompensationStatus status;

    /**
     * Service that needs to execute the compensation
     */
    private String targetService;

    /**
     * Specific action/method to invoke for compensation
     */
    private String compensationAction;

    /**
     * Data needed to execute the compensation
     */
    private Map<String, Object> compensationData;

    /**
     * Reason for compensation
     */
    private String reason;

    /**
     * Original error that triggered compensation
     */
    private String originalError;

    /**
     * Priority level for compensation execution
     */
    private CompensationPriority priority;

    /**
     * Maximum number of retry attempts
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Current retry attempt
     */
    @Builder.Default
    private int currentRetry = 0;

    /**
     * Timestamp when compensation was created
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Timestamp when compensation was last attempted
     */
    private LocalDateTime lastAttemptAt;

    /**
     * Timestamp when compensation completed successfully
     */
    private LocalDateTime completedAt;

    /**
     * User or system that initiated the compensation
     */
    private String initiatedBy;

    /**
     * Correlation ID for distributed tracing
     */
    private String correlationId;

    /**
     * Metadata for additional context
     */
    private Map<String, Object> metadata;

    public enum CompensationType {
        REFUND_PAYMENT,
        REVERSE_WALLET_DEBIT,
        REVERSE_WALLET_CREDIT,
        RELEASE_FUND_RESERVATION,
        CANCEL_AUTHORIZATION,
        REVERSE_LEDGER_ENTRY,
        CANCEL_PROVIDER_TRANSACTION,
        RESTORE_INVENTORY,
        CANCEL_NOTIFICATION,
        CUSTOM
    }

    public enum CompensationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        RETRYING,
        REQUIRES_MANUAL_INTERVENTION,
        CANCELLED
    }

    public enum CompensationPriority {
        CRITICAL,  // Financial transactions - process immediately
        HIGH,      // User-facing operations - process within 1 minute
        NORMAL,    // Background operations - process within 5 minutes
        LOW        // Non-critical operations - process when resources available
    }

    /**
     * Mark compensation as successful
     */
    public void markCompleted() {
        this.status = CompensationStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Mark compensation as failed
     */
    public void markFailed() {
        this.status = CompensationStatus.FAILED;
    }

    /**
     * Increment retry counter and update status
     */
    public void incrementRetry() {
        this.currentRetry++;
        this.lastAttemptAt = LocalDateTime.now();
        this.status = this.currentRetry >= this.maxRetries ?
            CompensationStatus.REQUIRES_MANUAL_INTERVENTION :
            CompensationStatus.RETRYING;
    }

    /**
     * Check if compensation has exceeded max retries
     */
    public boolean hasExceededMaxRetries() {
        return currentRetry >= maxRetries;
    }

    /**
     * Check if compensation requires manual intervention
     */
    public boolean requiresManualIntervention() {
        return status == CompensationStatus.REQUIRES_MANUAL_INTERVENTION;
    }
}
