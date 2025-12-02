package com.waqiti.compliance.enums;

/**
 * Incident Severity Enumeration
 *
 * Defines severity levels for compliance incidents to determine
 * SLA deadlines, escalation workflows, and priority assignment.
 *
 * Compliance: SOX 404, PCI DSS 10.x, GDPR Article 33
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
public enum IncidentSeverity {
    /**
     * LOW severity - minimal business impact
     * SLA: 30 days
     * Priority: P3
     * Escalation: Not required
     */
    LOW,

    /**
     * MEDIUM severity - moderate business impact
     * SLA: 7 business days
     * Priority: P2
     * Escalation: After 5 days if unresolved
     */
    MEDIUM,

    /**
     * HIGH severity - significant business impact
     * SLA: 24 hours
     * Priority: P1
     * Escalation: After 12 hours if unresolved
     */
    HIGH,

    /**
     * CRITICAL severity - severe business impact, regulatory risk
     * SLA: 1 hour
     * Priority: P0
     * Escalation: Immediate (C-suite notification)
     */
    CRITICAL;

    /**
     * Check if severity requires immediate attention
     *
     * @return true if critical or high severity
     */
    public boolean requiresImmediateAttention() {
        return this == CRITICAL || this == HIGH;
    }

    /**
     * Check if severity requires C-suite notification
     *
     * @return true if critical severity
     */
    public boolean requiresExecutiveNotification() {
        return this == CRITICAL;
    }

    /**
     * Check if severity requires regulatory reporting
     *
     * @return true if critical or high severity
     */
    public boolean requiresRegulatoryReporting() {
        return this == CRITICAL || this == HIGH;
    }

    /**
     * Get SLA deadline in hours
     *
     * @return SLA in hours
     */
    public int getSLAHours() {
        switch (this) {
            case CRITICAL:
                return 1;
            case HIGH:
                return 24;
            case MEDIUM:
                return 168; // 7 days
            case LOW:
            default:
                return 720; // 30 days
        }
    }

    /**
     * Get priority level (P0-P3)
     *
     * @return priority string
     */
    public String getPriority() {
        switch (this) {
            case CRITICAL:
                return "P0";
            case HIGH:
                return "P1";
            case MEDIUM:
                return "P2";
            case LOW:
            default:
                return "P3";
        }
    }

    /**
     * Get escalation threshold in hours (when to auto-escalate)
     *
     * @return escalation threshold in hours
     */
    public int getEscalationThresholdHours() {
        switch (this) {
            case CRITICAL:
                return 0; // Immediate escalation
            case HIGH:
                return 12; // Escalate after 12 hours
            case MEDIUM:
                return 120; // Escalate after 5 days
            case LOW:
            default:
                return 480; // Escalate after 20 days
        }
    }

    /**
     * Get escalation target role
     *
     * @return escalation target role
     */
    public String getEscalationTarget() {
        switch (this) {
            case CRITICAL:
                return "CHIEF_COMPLIANCE_OFFICER";
            case HIGH:
                return "COMPLIANCE_MANAGER";
            case MEDIUM:
                return "COMPLIANCE_TEAM_LEAD";
            case LOW:
            default:
                return "COMPLIANCE_ANALYST";
        }
    }

    /**
     * Check if severity allows auto-closure
     *
     * @return true if auto-closure allowed
     */
    public boolean allowsAutoClosure() {
        return this == LOW;
    }

    /**
     * Check if severity requires management approval for closure
     *
     * @return true if approval required
     */
    public boolean requiresApprovalForClosure() {
        return this == CRITICAL || this == HIGH;
    }

    /**
     * Get notification channels for severity
     *
     * @return array of notification channels
     */
    public String[] getNotificationChannels() {
        switch (this) {
            case CRITICAL:
                return new String[]{"EMAIL", "SMS", "PAGERDUTY", "SLACK"};
            case HIGH:
                return new String[]{"EMAIL", "SLACK"};
            case MEDIUM:
                return new String[]{"EMAIL"};
            case LOW:
            default:
                return new String[]{}; // No automatic notifications
        }
    }

    /**
     * Check if severity is higher than or equal to target
     *
     * @param target target severity to compare
     * @return true if this severity is >= target severity
     */
    public boolean isAtLeast(IncidentSeverity target) {
        return this.ordinal() >= target.ordinal();
    }
}
