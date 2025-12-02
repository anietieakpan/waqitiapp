package com.waqiti.compliance.contracts.dto;

/**
 * Overall compliance status
 */
public enum ComplianceStatus {
    /**
     * Fully compliant - all checks passed
     */
    COMPLIANT,

    /**
     * Partially compliant - some non-critical issues
     */
    PARTIALLY_COMPLIANT,

    /**
     * Non-compliant - critical issues found
     */
    NON_COMPLIANT,

    /**
     * Critical compliance violation - immediate action required
     */
    CRITICAL,

    /**
     * Validation in progress
     */
    IN_PROGRESS,

    /**
     * Validation pending/scheduled
     */
    PENDING,

    /**
     * Validation failed due to error
     */
    ERROR,

    /**
     * Unknown status
     */
    UNKNOWN
}
