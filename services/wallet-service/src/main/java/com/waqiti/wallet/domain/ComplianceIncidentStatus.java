package com.waqiti.wallet.domain;

/**
 * Compliance Incident Status Enum
 *
 * Represents the lifecycle status of compliance incidents requiring investigation,
 * resolution, and potential regulatory reporting.
 *
 * STATUS WORKFLOW:
 * CREATED -> REQUIRES_IMMEDIATE_REVIEW -> UNDER_INVESTIGATION -> RESOLVED/ESCALATED
 *
 * SLA TARGETS:
 * - REQUIRES_IMMEDIATE_REVIEW: < 2 hours response time
 * - UNDER_INVESTIGATION: < 24 hours for HIGH severity, < 72 hours for MEDIUM
 * - REGULATORY_REPORTED: < 30 days for FinCEN SAR, < 15 days for Form 8300
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
public enum ComplianceIncidentStatus {

    /**
     * Incident created but not yet reviewed
     */
    CREATED,

    /**
     * Critical incident requiring immediate compliance team review
     * SLA: 2 hours for initial review
     */
    REQUIRES_IMMEDIATE_REVIEW,

    /**
     * Incident is under active investigation
     */
    UNDER_INVESTIGATION,

    /**
     * Investigation completed, awaiting final resolution
     */
    PENDING_RESOLUTION,

    /**
     * Incident resolved, no regulatory action required
     */
    RESOLVED,

    /**
     * Incident escalated to executive/regulatory level
     */
    ESCALATED,

    /**
     * Incident reported to regulatory authorities (FinCEN, IRS, OFAC)
     */
    REGULATORY_REPORTED,

    /**
     * Incident closed after full resolution and documentation
     */
    CLOSED,

    /**
     * Incident cancelled (false positive or duplicate)
     */
    CANCELLED
}
