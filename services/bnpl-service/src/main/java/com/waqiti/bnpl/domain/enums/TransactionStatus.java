package com.waqiti.bnpl.domain.enums;

/**
 * Status of a BNPL transaction
 */
public enum TransactionStatus {
    PENDING,     // Transaction initiated but not processed
    PROCESSING,  // Transaction is being processed
    COMPLETED,   // Transaction successfully completed
    FAILED,      // Transaction failed
    CANCELLED,   // Transaction was cancelled
    REFUNDED,    // Transaction was refunded
    REVERSED     // Transaction was reversed
}