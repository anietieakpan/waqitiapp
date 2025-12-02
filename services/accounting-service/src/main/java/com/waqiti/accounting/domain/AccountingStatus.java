package com.waqiti.accounting.domain;

/**
 * Accounting Status enumeration
 * Status for accounting operations and results
 */
public enum AccountingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REVERSED
}
