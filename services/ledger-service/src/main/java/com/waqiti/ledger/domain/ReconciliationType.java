package com.waqiti.ledger.domain;

/**
 * Reconciliation Type Enum
 *
 * Defines the different types of reconciliation processes.
 */
public enum ReconciliationType {
    /**
     * Daily balance reconciliation
     */
    DAILY_BALANCE,

    /**
     * Transaction-level reconciliation
     */
    TRANSACTION_LEVEL,

    /**
     * Nostro account reconciliation (our account with external bank)
     */
    NOSTRO_ACCOUNT,

    /**
     * Vostro account reconciliation (external account with us)
     */
    VOSTRO_ACCOUNT,

    /**
     * Merchant settlement reconciliation
     */
    MERCHANT_SETTLEMENT,

    /**
     * Payment processor reconciliation
     */
    PAYMENT_PROCESSOR,

    /**
     * Bank statement reconciliation
     */
    BANK_STATEMENT,

    /**
     * Internal ledger reconciliation
     */
    INTERNAL_LEDGER,

    /**
     * Month-end reconciliation
     */
    MONTH_END,

    /**
     * Year-end reconciliation
     */
    YEAR_END
}
