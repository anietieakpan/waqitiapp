package com.waqiti.compliance.contracts.dto.aml;

/**
 * Types of AML cases
 */
public enum AMLCaseType {
    /**
     * Suspicious Activity Report (SAR) case
     */
    SAR,

    /**
     * Currency Transaction Report (CTR) case
     */
    CTR,

    /**
     * Transaction monitoring alert
     */
    TRANSACTION_MONITORING,

    /**
     * Customer due diligence investigation
     */
    CDD,

    /**
     * Enhanced due diligence investigation
     */
    EDD,

    /**
     * Sanctions screening hit
     */
    SANCTIONS_HIT,

    /**
     * PEP (Politically Exposed Person) screening
     */
    PEP_SCREENING,

    /**
     * Structuring/Smurfing pattern detected
     */
    STRUCTURING,

    /**
     * Velocity check threshold exceeded
     */
    VELOCITY_ALERT,

    /**
     * Geographic risk alert
     */
    GEOGRAPHIC_RISK,

    /**
     * Unusual pattern detected
     */
    UNUSUAL_PATTERN,

    /**
     * Large cash transaction
     */
    LARGE_CASH,

    /**
     * Cross-border transaction monitoring
     */
    CROSS_BORDER,

    /**
     * High-risk customer review
     */
    HIGH_RISK_CUSTOMER,

    /**
     * Ongoing monitoring alert
     */
    ONGOING_MONITORING,

    /**
     * Manual review requested
     */
    MANUAL_REVIEW,

    /**
     * Other type
     */
    OTHER
}
