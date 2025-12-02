package com.waqiti.audit.domain;

/**
 * Audit risk level enumeration for categorizing audit events by risk
 */
public enum AuditRiskLevel {
    /**
     * Low risk - routine operations with minimal impact
     */
    LOW("Low Risk", 1),

    /**
     * Medium risk - operations requiring standard oversight
     */
    MEDIUM("Medium Risk", 2),

    /**
     * High risk - operations requiring elevated monitoring
     */
    HIGH("High Risk", 3),

    /**
     * Critical risk - operations requiring immediate attention
     */
    CRITICAL("Critical Risk", 4);

    private final String displayName;
    private final int severity;

    AuditRiskLevel(String displayName, int severity) {
        this.displayName = displayName;
        this.severity = severity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSeverity() {
        return severity;
    }

    /**
     * Check if this risk level is higher than the specified level
     */
    public boolean isHigherThan(AuditRiskLevel other) {
        return this.severity > other.severity;
    }

    /**
     * Check if this risk level is lower than the specified level
     */
    public boolean isLowerThan(AuditRiskLevel other) {
        return this.severity < other.severity;
    }
}
