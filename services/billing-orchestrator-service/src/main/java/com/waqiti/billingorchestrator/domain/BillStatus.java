package com.waqiti.billingorchestrator.domain;

/**
 * Bill payment status enum
 * Migrated from billing-service
 */
public enum BillStatus {
    UNPAID,
    PAID,
    OVERDUE,
    CANCELLED,
    PENDING,
    PROCESSING,
    FAILED
}
