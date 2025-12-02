package com.waqiti.compliance.contracts.dto.aml;

/**
 * Priority levels for AML cases
 */
public enum AMLPriority {
    /**
     * Critical - immediate review required
     */
    CRITICAL,

    /**
     * High - review within 24 hours
     */
    HIGH,

    /**
     * Medium - review within 3 days
     */
    MEDIUM,

    /**
     * Low - review within 7 days
     */
    LOW,

    /**
     * Routine - review in normal queue
     */
    ROUTINE
}
