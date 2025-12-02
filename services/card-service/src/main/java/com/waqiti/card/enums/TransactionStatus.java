package com.waqiti.card.enums;

/**
 * Transaction status enumeration
 * Represents the current state of a card transaction
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum TransactionStatus {
    /**
     * Transaction initiated, awaiting authorization
     */
    PENDING,

    /**
     * Transaction authorized but not yet settled
     */
    AUTHORIZED,

    /**
     * Transaction declined by issuer
     */
    DECLINED,

    /**
     * Transaction completed and settled
     */
    COMPLETED,

    /**
     * Transaction reversed/voided
     */
    REVERSED,

    /**
     * Transaction failed (technical error)
     */
    FAILED,

    /**
     * Transaction timed out
     */
    TIMEOUT,

    /**
     * Transaction being settled
     */
    SETTLING,

    /**
     * Transaction settled successfully
     */
    SETTLED,

    /**
     * Transaction disputed by cardholder
     */
    DISPUTED,

    /**
     * Transaction blocked by fraud system
     */
    FRAUD_BLOCKED,

    /**
     * Transaction cancelled by user/system
     */
    CANCELLED,

    /**
     * Transaction pending 3DS verification
     */
    PENDING_3DS,

    /**
     * Transaction requires additional verification
     */
    REQUIRES_VERIFICATION
}
