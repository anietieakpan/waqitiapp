package com.waqiti.payment.domain;

public enum SettlementStatus {
    INITIATED,
    PENDING_APPROVAL,
    PROCESSING,
    TRANSFERRED,
    TRANSFER_PENDING,
    TRANSFER_FAILED,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED
}
