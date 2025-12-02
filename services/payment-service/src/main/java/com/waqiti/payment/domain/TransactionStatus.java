package com.waqiti.payment.domain;

public enum TransactionStatus {
    INITIATED,
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REVERSED,
    REFUNDED
}
