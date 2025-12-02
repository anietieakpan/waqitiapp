package com.waqiti.wallet.domain;

/**
 * Represents the possible states of a transaction
 */
public enum TransactionStatus {
    PENDING, // Transaction is created but not yet processed
    IN_PROGRESS, // Transaction is being processed
    COMPLETED, // Transaction has been successfully completed
    FAILED, // Transaction failed to complete
    BLOCKED, // Transaction blocked for compliance/fraud reasons
    MONITORING_BLOCKED, // Transaction blocked with enhanced monitoring
    AUTO_REVIEW_BLOCKED, // Transaction blocked pending auto-review
    MANUAL_REVIEW_REQUIRED, // Transaction requires manual review
    DELAYED, // Transaction execution is delayed
    SCHEDULED, // Transaction is scheduled for future execution
    PROCESSING, // Transaction is actively being processed
    CANCELLED // Transaction was cancelled
}
