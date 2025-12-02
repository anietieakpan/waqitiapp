package com.waqiti.payment.entity;

/**
 * Payment and ACH Status enumeration
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REVERSED,
    RETURNED,
    SETTLED
}
