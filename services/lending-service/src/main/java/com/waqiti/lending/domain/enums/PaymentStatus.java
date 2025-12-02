package com.waqiti.lending.domain.enums;

/**
 * Payment Status
 */
public enum PaymentStatus {
    PENDING,            // Payment initiated but not processed
    PROCESSING,         // Payment being processed
    COMPLETED,          // Payment successfully completed
    FAILED,             // Payment failed
    CANCELLED,          // Payment cancelled
    REVERSED,           // Payment reversed/refunded
    SCHEDULED,          // Future scheduled payment
    OVERDUE             // Payment past due
}
