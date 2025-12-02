package com.waqiti.payment.entity;

/**
 * ACH Transfer Status enumeration
 */
public enum ACHTransferStatus {
    PENDING,        // Initial state
    PROCESSING,     // Being processed in batch
    SUBMITTED,      // Submitted to ACH network
    COMPLETED,      // Successfully completed
    FAILED,         // Failed to process
    CANCELLED,      // Cancelled by user
    RETURNED,       // Returned by bank
    REVERSED        // Reversed due to return/dispute
}