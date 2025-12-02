package com.waqiti.audit.domain;

/**
 * Escalation levels for critical alerts
 */
public enum EscalationLevel {
    LOW("Low - Standard escalation"),
    MEDIUM("Medium - Elevated escalation"),
    HIGH("High - Priority escalation"),
    URGENT("Urgent - Immediate attention required"),
    EMERGENCY("Emergency - Critical response required"),
    CATASTROPHIC("Catastrophic - Maximum escalation level");

    private final String description;

    EscalationLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
