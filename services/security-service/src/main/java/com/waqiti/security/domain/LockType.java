package com.waqiti.security.domain;

/**
 * Lock Type Enum
 *
 * Defines the types of account locks that can be applied.
 */
public enum LockType {
    /**
     * Temporary lock - will be automatically unlocked after a specified duration
     */
    TEMPORARY,

    /**
     * Permanent lock - requires manual intervention to unlock
     */
    PERMANENT,

    /**
     * Conditional lock - unlocked when certain conditions are met
     */
    CONDITIONAL,

    /**
     * Soft lock - restricts certain operations but allows basic account access
     */
    SOFT,

    /**
     * Hard lock - complete account lockout, no access permitted
     */
    HARD
}
