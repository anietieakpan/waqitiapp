package com.waqiti.common.metrics.service;

public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED,
    DISPUTED,
    HELD,
    EXPIRED
}