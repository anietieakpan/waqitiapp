package com.waqiti.virtualcard.domain;

/**
 * Transaction Status Enumeration
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    COMPLETED,
    DECLINED,
    REJECTED,
    CANCELLED,
    FAILED,
    EXPIRED,
    REVERSED,
    VOIDED,
    SETTLED,
    DISPUTED,
    CHARGEBACK,
    REFUNDED,
    PARTIALLY_REFUNDED,
    AUTHORIZED,
    CAPTURED,
    REVIEW,
    HELD,
    TIMEOUT,
    ERROR,
    FRAUDULENT,
    SUSPICIOUS,
    BLOCKED
}