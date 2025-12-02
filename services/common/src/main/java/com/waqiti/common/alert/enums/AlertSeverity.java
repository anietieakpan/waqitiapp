package com.waqiti.common.alert.enums;

/**
 * Alert Severity Levels
 *
 * Defines the severity/priority levels for alerts and incidents.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
public enum AlertSeverity {
    /**
     * CRITICAL - Immediate action required
     * - System down, data loss, security breach
     * - PagerDuty: Phone call + SMS + Push
     * - Response time: < 5 minutes
     */
    CRITICAL,

    /**
     * HIGH - Urgent attention required
     * - Service degraded, payment failures, fraud detected
     * - PagerDuty: SMS + Push
     * - Response time: < 15 minutes
     */
    HIGH,

    /**
     * MEDIUM - Important but not urgent
     * - Performance degradation, elevated error rates
     * - PagerDuty: Push notification
     * - Response time: < 1 hour
     */
    MEDIUM,

    /**
     * LOW - Informational, no immediate action
     * - Warnings, minor issues, successful resolutions
     * - PagerDuty: Email only
     * - Response time: Next business day
     */
    LOW,

    /**
     * INFO - Informational only
     * - Status updates, routine events
     * - PagerDuty: Optional logging only
     * - Response time: Not applicable
     */
    INFO
}
