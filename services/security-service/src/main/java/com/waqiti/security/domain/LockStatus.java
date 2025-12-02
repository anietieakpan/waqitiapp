package com.waqiti.security.domain;

/**
 * Lock Status Enum
 *
 * Defines the lifecycle status of an account lock.
 */
public enum LockStatus {
    /**
     * Lock request has been initiated but not yet processed
     */
    INITIATED,

    /**
     * Lock is pending completion of required security actions
     */
    PENDING,

    /**
     * Lock is active and account is locked
     */
    ACTIVE,

    /**
     * Lock is scheduled for future activation
     */
    SCHEDULED,

    /**
     * Lock has been released/unlocked
     */
    RELEASED,

    /**
     * Lock has expired (for temporary locks)
     */
    EXPIRED,

    /**
     * Lock processing failed
     */
    FAILED,

    /**
     * Lock has been cancelled before activation
     */
    CANCELLED,

    /**
     * Lock has been upgraded to a higher severity
     */
    UPGRADED,

    /**
     * Lock is under review
     */
    UNDER_REVIEW
}
