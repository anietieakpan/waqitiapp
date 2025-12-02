package com.waqiti.gdpr.domain;

/**
 * Result of an audited operation
 */
public enum AuditResult {
    /**
     * Operation completed successfully
     */
    SUCCESS,

    /**
     * Operation failed
     */
    FAILURE,

    /**
     * Operation partially completed
     */
    PARTIAL,

    /**
     * Operation was denied due to authorization
     */
    DENIED,

    /**
     * Operation is still pending
     */
    PENDING
}
