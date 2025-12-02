package com.waqiti.transaction.domain;

/**
 * Account Status Enum
 *
 * @author Waqiti Platform Team
 */
public enum AccountStatus {
    /**
     * Account is active and can perform transactions
     */
    ACTIVE,

    /**
     * Account is temporarily frozen (no debits allowed)
     */
    FROZEN,

    /**
     * Account is suspended (no transactions allowed)
     */
    SUSPENDED,

    /**
     * Account is permanently closed
     */
    CLOSED,

    /**
     * Account is dormant (inactive for extended period)
     */
    DORMANT,

    /**
     * Account is pending activation
     */
    PENDING_ACTIVATION
}
