package com.waqiti.user.kafka.dlq;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Severity levels for DLQ events
 * Determines urgency and escalation path
 */
@Getter
@RequiredArgsConstructor
public enum DlqSeverityLevel {

    /**
     * P0 - Critical: Immediate action required, affects customer funds or security
     * Examples: Fraud alerts, payment failures, security breaches
     * SLA: 15 minutes
     */
    CRITICAL(0, "Critical", 15),

    /**
     * P1 - High: Requires urgent attention, affects customer experience
     * Examples: KYC failures, authentication issues, account access problems
     * SLA: 1 hour
     */
    HIGH(1, "High", 60),

    /**
     * P2 - Medium: Should be addressed but not urgent
     * Examples: Profile updates, preference changes, notification delivery
     * SLA: 4 hours
     */
    MEDIUM(2, "Medium", 240),

    /**
     * P3 - Low: Can be handled during business hours
     * Examples: Analytics events, audit logs, informational messages
     * SLA: 24 hours
     */
    LOW(3, "Low", 1440),

    /**
     * P4 - Info: Informational only, no action required
     * Examples: Debug events, telemetry data
     * SLA: No SLA
     */
    INFO(4, "Info", Integer.MAX_VALUE);

    private final int priority;
    private final String label;
    private final int slaMinutes;

    public boolean isCritical() {
        return this == CRITICAL;
    }

    public boolean requiresPagerDuty() {
        return this == CRITICAL || this == HIGH;
    }
}
