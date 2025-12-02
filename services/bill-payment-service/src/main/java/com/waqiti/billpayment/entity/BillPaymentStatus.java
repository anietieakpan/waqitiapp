package com.waqiti.billpayment.entity;

/**
 * Status values for bill payment transactions
 */
public enum BillPaymentStatus {
    /**
     * Payment initiated but not yet processed
     */
    PENDING,

    /**
     * Payment scheduled for future date
     */
    SCHEDULED,

    /**
     * Payment is being processed (wallet debited, sent to biller)
     */
    PROCESSING,

    /**
     * Payment successfully completed
     */
    COMPLETED,

    /**
     * Payment failed - may be retried
     */
    FAILED,

    /**
     * Payment rejected by biller
     */
    REJECTED,

    /**
     * Payment cancelled by user before processing
     */
    CANCELLED,

    /**
     * Payment refunded to user
     */
    REFUNDED,

    /**
     * Payment is under review (fraud check, manual review)
     */
    UNDER_REVIEW
}
