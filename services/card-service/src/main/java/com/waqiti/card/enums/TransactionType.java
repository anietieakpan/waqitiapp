package com.waqiti.card.enums;

/**
 * Transaction type enumeration
 * Defines different types of card transactions
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum TransactionType {
    /**
     * Point of sale purchase
     */
    PURCHASE,

    /**
     * ATM withdrawal
     */
    WITHDRAWAL,

    /**
     * ATM balance inquiry
     */
    BALANCE_INQUIRY,

    /**
     * Cash advance on credit card
     */
    CASH_ADVANCE,

    /**
     * Refund/credit to card
     */
    REFUND,

    /**
     * Payment to card (paying off balance)
     */
    PAYMENT,

    /**
     * Pre-authorization (hotel, gas station)
     */
    PRE_AUTHORIZATION,

    /**
     * Authorization reversal
     */
    REVERSAL,

    /**
     * Fee charge (monthly fee, late fee, etc.)
     */
    FEE,

    /**
     * Interest charge
     */
    INTEREST,

    /**
     * Balance transfer
     */
    BALANCE_TRANSFER,

    /**
     * Online/e-commerce purchase
     */
    ONLINE_PURCHASE,

    /**
     * Contactless purchase
     */
    CONTACTLESS_PURCHASE,

    /**
     * Recurring/subscription payment
     */
    RECURRING_PAYMENT,

    /**
     * Chargeback
     */
    CHARGEBACK,

    /**
     * Adjustment (manual correction)
     */
    ADJUSTMENT
}
