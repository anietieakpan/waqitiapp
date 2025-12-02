package com.waqiti.payment.entity;

/**
 * Instant deposit status enumeration
 */
public enum InstantDepositStatus {
    PENDING,        // Initial state
    PROCESSING,     // Being processed through card networks
    COMPLETED,      // Successfully completed
    FAILED          // Failed to process
}