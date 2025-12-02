package com.waqiti.card.enums;

/**
 * Authorization status enumeration
 * Represents the outcome of an authorization request
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum AuthorizationStatus {
    /**
     * Authorization pending processing
     */
    PENDING,

    /**
     * Authorization approved
     */
    APPROVED,

    /**
     * Authorization declined
     */
    DECLINED,

    /**
     * Authorization partially approved (for partial amount)
     */
    PARTIAL_APPROVAL,

    /**
     * Authorization expired (not captured within time limit)
     */
    EXPIRED,

    /**
     * Authorization reversed
     */
    REVERSED,

    /**
     * Authorization captured (settled)
     */
    CAPTURED,

    /**
     * Authorization failed (technical error)
     */
    FAILED,

    /**
     * Authorization timed out
     */
    TIMEOUT,

    /**
     * Authorization blocked by fraud rules
     */
    FRAUD_BLOCKED,

    /**
     * Authorization requires additional verification
     */
    REQUIRES_VERIFICATION
}
