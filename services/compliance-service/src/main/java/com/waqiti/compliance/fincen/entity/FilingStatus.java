package com.waqiti.compliance.fincen.entity;

/**
 * Filing Status for Manual Filing Queue
 */
public enum FilingStatus {
    /**
     * SAR is pending manual filing
     */
    PENDING,

    /**
     * SAR filing is in progress
     */
    IN_PROGRESS,

    /**
     * SAR has been manually filed to FinCEN
     */
    MANUALLY_FILED,

    /**
     * SAR filing is overdue (SLA violated)
     */
    OVERDUE,

    /**
     * SAR filing failed and requires escalation
     */
    FAILED,

    /**
     * SAR was cancelled or is no longer needed
     */
    CANCELLED
}
