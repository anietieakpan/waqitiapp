package com.waqiti.payment.domain;

/**
 * Represents the possible states of a payment request
 */
public enum PaymentRequestStatus {
    PENDING,    // Payment request is pending approval
    APPROVED,   // Payment request has been approved
    REJECTED,   // Payment request has been rejected
    CANCELED,   // Payment request has been canceled
    EXPIRED     // Payment request has expired
}