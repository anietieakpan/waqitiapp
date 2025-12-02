package com.waqiti.compliance.enums;

/**
 * Financial Crime Severity Enumeration
 *
 * Defines severity levels for financial crimes to determine
 * response protocols and escalation workflows.
 *
 * Compliance: BSA/AML, FinCEN SAR Requirements
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
public enum CrimeSeverity {
    /**
     * Low severity - monitoring required
     * No immediate action needed
     */
    LOW,

    /**
     * Medium severity - investigation required
     * Timeline: 7 business days
     */
    MEDIUM,

    /**
     * High severity - urgent investigation required
     * Timeline: 24 hours
     * Requires compliance team notification
     */
    HIGH,

    /**
     * Critical severity - immediate action required
     * Timeline: Immediate (< 1 hour)
     * Requires account freeze, FinCEN SAR filing, law enforcement notification
     */
    CRITICAL;

    /**
     * Check if severity requires immediate action
     *
     * @return true if critical or high severity
     */
    public boolean requiresImmediateAction() {
        return this == CRITICAL || this == HIGH;
    }

    /**
     * Check if severity requires account freeze
     *
     * @return true if critical severity
     */
    public boolean requiresAccountFreeze() {
        return this == CRITICAL;
    }

    /**
     * Check if severity requires FinCEN SAR filing
     *
     * @return true if critical or high severity
     */
    public boolean requiresSARFiling() {
        return this == CRITICAL || this == HIGH;
    }

    /**
     * Check if severity requires law enforcement notification
     *
     * @return true if critical severity
     */
    public boolean requiresLawEnforcementNotification() {
        return this == CRITICAL;
    }

    /**
     * Get response timeline in hours
     *
     * @return maximum hours for response
     */
    public int getResponseTimelineHours() {
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
     * Get escalation level (0-3)
     *
     * @return escalation level
     */
    public int getEscalationLevel() {
        switch (this) {
            case CRITICAL:
                return 3;
            case HIGH:
                return 2;
            case MEDIUM:
                return 1;
            case LOW:
            default:
                return 0;
        }
    }
}
