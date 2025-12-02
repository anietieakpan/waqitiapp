package com.waqiti.billpayment.entity;

/**
 * Status values for bills throughout their lifecycle
 */
public enum BillStatus {
    /**
     * Bill imported and awaiting payment
     */
    UNPAID,

    /**
     * Partial payment made, remaining balance due
     */
    PARTIALLY_PAID,

    /**
     * Full payment completed
     */
    PAID,

    /**
     * Bill is overdue (past due date and unpaid)
     */
    OVERDUE,

    /**
     * Payment scheduled but not yet processed
     */
    SCHEDULED,

    /**
     * Payment in progress with biller
     */
    PROCESSING,

    /**
     * Payment failed - needs retry
     */
    FAILED,

    /**
     * Bill disputed by user
     */
    DISPUTED,

    /**
     * Bill cancelled (deleted or voided)
     */
    CANCELLED,

    /**
     * Payment refunded by biller
     */
    REFUNDED
}
