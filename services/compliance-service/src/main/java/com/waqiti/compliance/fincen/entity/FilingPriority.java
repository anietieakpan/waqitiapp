package com.waqiti.compliance.fincen.entity;

/**
 * Filing Priority for Manual Filing Queue
 *
 * EXPEDITED: 24-hour SLA (urgent, suspicious activity)
 * STANDARD: 72-hour SLA (normal filing)
 */
public enum FilingPriority {
    /**
     * Expedited filing (24-hour SLA)
     * Used for urgent suspicious activity reports
     */
    EXPEDITED,

    /**
     * Standard filing (72-hour SLA)
     * Used for normal SAR filings
     */
    STANDARD
}
