package com.waqiti.alerting.dto;

/**
 * Alert severity levels aligned with PagerDuty and industry standards
 */
public enum AlertSeverity {
    /**
     * CRITICAL - Immediate action required, system down or data loss imminent
     * Examples: Payment processing stopped, fraud undetected, data corruption
     */
    CRITICAL,

    /**
     * HIGH - Urgent action required, major functionality impaired
     * Examples: High DLQ message count, elevated fraud, compliance violations
     */
    HIGH,

    /**
     * MEDIUM - Action required within business hours
     * Examples: Performance degradation, minor feature issues
     */
    MEDIUM,

    /**
     * LOW - Informational, no immediate action required
     * Examples: Configuration warnings, non-critical notifications
     */
    LOW
}
