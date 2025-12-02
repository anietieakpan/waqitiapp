package com.waqiti.compliance.contracts.dto;

/**
 * Priority levels for compliance validation requests
 */
public enum ValidationPriority {
    /**
     * Critical - process immediately
     */
    CRITICAL,

    /**
     * High priority - process before normal queue
     */
    HIGH,

    /**
     * Normal priority - standard queue processing
     */
    NORMAL,

    /**
     * Low priority - can be deferred
     */
    LOW,

    /**
     * Scheduled - run at specified time
     */
    SCHEDULED
}
