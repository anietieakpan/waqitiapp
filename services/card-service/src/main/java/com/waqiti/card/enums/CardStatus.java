package com.waqiti.card.enums;

/**
 * Card status enumeration
 * Represents the current lifecycle status of a card
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum CardStatus {
    /**
     * Card has been created but not yet activated
     */
    PENDING_ACTIVATION,

    /**
     * Card is active and can be used for transactions
     */
    ACTIVE,

    /**
     * Card is temporarily blocked (can be unblocked)
     */
    BLOCKED,

    /**
     * Card is blocked due to suspected fraud
     */
    FRAUD_BLOCKED,

    /**
     * Card is blocked due to lost/stolen report
     */
    LOST_STOLEN,

    /**
     * Card has expired (past expiry date)
     */
    EXPIRED,

    /**
     * Card has been permanently cancelled
     */
    CANCELLED,

    /**
     * Card has been replaced by a new card
     */
    REPLACED,

    /**
     * Card is suspended (administrative hold)
     */
    SUSPENDED,

    /**
     * Card activation is pending user action
     */
    PENDING_USER_ACTIVATION,

    /**
     * Card is being shipped to customer
     */
    IN_TRANSIT
}
