package com.waqiti.wallet.domain;

/**
 * Enum representing the status of a wallet transfer operation.
 */
public enum TransferStatus {
    INITIATED,
    VALIDATING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED,
    CANCELLED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXPIRED
}