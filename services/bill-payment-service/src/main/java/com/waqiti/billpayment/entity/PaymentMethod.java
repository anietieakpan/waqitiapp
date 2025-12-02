package com.waqiti.billpayment.entity;

/**
 * Payment methods supported for bill payments
 */
public enum PaymentMethod {
    /**
     * Payment from Waqiti wallet balance
     */
    WALLET,

    /**
     * Direct bank account debit (ACH)
     */
    BANK_ACCOUNT,

    /**
     * Debit card payment
     */
    DEBIT_CARD,

    /**
     * Credit card payment
     */
    CREDIT_CARD,

    /**
     * Check payment (paper or electronic)
     */
    CHECK,

    /**
     * Cash payment at agent location
     */
    CASH
}
