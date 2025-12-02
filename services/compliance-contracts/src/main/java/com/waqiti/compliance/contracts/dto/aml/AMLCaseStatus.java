package com.waqiti.compliance.contracts.dto.aml;

/**
 * Status of an AML case
 */
public enum AMLCaseStatus {
    /**
     * Case created, pending assignment
     */
    NEW,

    /**
     * Case assigned, awaiting review
     */
    ASSIGNED,

    /**
     * Case under active investigation
     */
    INVESTIGATING,

    /**
     * Additional information requested
     */
    INFO_REQUESTED,

    /**
     * Escalated to senior compliance officer
     */
    ESCALATED,

    /**
     * Pending SAR filing decision
     */
    PENDING_SAR,

    /**
     * SAR filed with FinCEN
     */
    SAR_FILED,

    /**
     * No suspicious activity detected, closed
     */
    CLOSED_NO_ACTION,

    /**
     * Suspicious activity confirmed, action taken
     */
    CLOSED_ACTION_TAKEN,

    /**
     * Case closed as false positive
     */
    CLOSED_FALSE_POSITIVE,

    /**
     * Account/transaction blocked
     */
    BLOCKED,

    /**
     * Case referred to law enforcement
     */
    REFERRED_LE,

    /**
     * Case on hold pending additional info
     */
    ON_HOLD,

    /**
     * Case reopened for additional review
     */
    REOPENED
}
