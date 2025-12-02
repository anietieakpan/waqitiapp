package com.waqiti.compliance.contracts.dto;

/**
 * Status of a compliance finding
 */
public enum FindingStatus {
    /**
     * New finding, not yet reviewed
     */
    NEW,

    /**
     * Finding confirmed and accepted
     */
    OPEN,

    /**
     * Remediation in progress
     */
    IN_PROGRESS,

    /**
     * Remediation completed, awaiting verification
     */
    RESOLVED,

    /**
     * Resolution verified and finding closed
     */
    CLOSED,

    /**
     * Finding was false positive
     */
    FALSE_POSITIVE,

    /**
     * Risk accepted, no remediation planned
     */
    ACCEPTED_RISK,

    /**
     * Transferred to another team/system
     */
    TRANSFERRED,

    /**
     * Finding reopened after verification failed
     */
    REOPENED,

    /**
     * Duplicate of another finding
     */
    DUPLICATE
}
