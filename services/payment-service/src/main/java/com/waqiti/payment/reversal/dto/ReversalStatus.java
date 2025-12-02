package com.waqiti.payment.reversal.dto;

/**
 * Status of payment reversal
 */
public enum ReversalStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    PARTIALLY_REVERSED,
    CANCELLED
}
