package com.waqiti.common.monitoring;

/**
 * Alert Severity Levels
 *
 * Maps to incident priorities:
 * - CRITICAL = P0 (immediate on-call escalation)
 * - ERROR = P1 (urgent, within hours)
 * - WARNING = P2 (address within day)
 * - INFO = P3 (informational, no action required)
 */
public enum AlertSeverity {
    /**
     * P0 - Critical
     * Production outage, data loss, security breach
     * Triggers: PagerDuty, Slack, Email, SMS
     */
    CRITICAL,

    /**
     * P1 - Error
     * Service degradation, failed transactions, compliance violations
     * Triggers: PagerDuty, Slack, Email
     */
    ERROR,

    /**
     * P2 - Warning
     * Resource constraints, performance degradation, approaching limits
     * Triggers: Slack, Email
     */
    WARNING,

    /**
     * P3 - Info
     * Informational events, successful recoveries, routine notifications
     * Triggers: Slack only
     */
    INFO
}
