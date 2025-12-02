package com.waqiti.common.compliance;

/**
 * Compliance report priority levels
 *
 * Defines urgency and importance levels for compliance reports,
 * affecting processing order, notification levels, and escalation procedures.
 */
public enum ComplianceReportPriority {

    /**
     * Critical priority - requires immediate action
     * Used for urgent regulatory filings, security incidents, and critical compliance violations
     */
    CRITICAL("Critical", "Immediate action required - highest priority", 1, true, true),

    /**
     * High priority - requires prompt action
     * Used for important regulatory deadlines and significant compliance issues
     */
    HIGH("High", "High priority - prompt action required", 2, true, false),

    /**
     * Medium priority - standard processing
     * Used for regular compliance reports with standard deadlines
     */
    MEDIUM("Medium", "Medium priority - standard processing timeline", 3, false, false),

    /**
     * Low priority - can be processed in normal course
     * Used for routine reports and informational filings
     */
    LOW("Low", "Low priority - routine processing", 4, false, false),

    /**
     * Routine - regular scheduled reports
     * Used for periodic compliance reports with no special urgency
     */
    ROUTINE("Routine", "Routine filing - regular schedule", 5, false, false);

    private final String displayName;
    private final String description;
    private final int priorityOrder;
    private final boolean requiresEscalation;
    private final boolean requiresImmediateNotification;

    ComplianceReportPriority(String displayName, String description, int priorityOrder,
                            boolean requiresEscalation, boolean requiresImmediateNotification) {
        this.displayName = displayName;
        this.description = description;
        this.priorityOrder = priorityOrder;
        this.requiresEscalation = requiresEscalation;
        this.requiresImmediateNotification = requiresImmediateNotification;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getPriorityOrder() { return priorityOrder; }
    public boolean requiresEscalation() { return requiresEscalation; }
    public boolean requiresImmediateNotification() { return requiresImmediateNotification; }

    /**
     * Check if this priority is higher than another
     */
    public boolean isHigherThan(ComplianceReportPriority other) {
        return this.priorityOrder < other.priorityOrder;
    }

    /**
     * Check if this priority level requires urgent processing
     */
    public boolean isUrgent() {
        return this == CRITICAL || this == HIGH;
    }

    /**
     * Get SLA hours for this priority level
     */
    public int getSlaHours() {
        switch (this) {
            case CRITICAL: return 4;   // 4 hours
            case HIGH: return 24;      // 24 hours
            case MEDIUM: return 72;    // 3 days
            case LOW: return 168;      // 1 week
            case ROUTINE: return 720;  // 30 days
            default: return 168;
        }
    }

    /**
     * Convert from ComplianceReport.ReportPriority to ComplianceReportPriority
     */
    public static ComplianceReportPriority fromReportPriority(ComplianceReport.ReportPriority reportPriority) {
        if (reportPriority == null) return MEDIUM;

        switch (reportPriority) {
            case CRITICAL: return CRITICAL;
            case HIGH: return HIGH;
            case MEDIUM: return MEDIUM;
            case LOW: return LOW;
            case ROUTINE: return ROUTINE;
            default: return MEDIUM;
        }
    }

    /**
     * Convert to ComplianceReport.ReportPriority
     */
    public ComplianceReport.ReportPriority toReportPriority() {
        switch (this) {
            case CRITICAL: return ComplianceReport.ReportPriority.CRITICAL;
            case HIGH: return ComplianceReport.ReportPriority.HIGH;
            case MEDIUM: return ComplianceReport.ReportPriority.MEDIUM;
            case LOW: return ComplianceReport.ReportPriority.LOW;
            case ROUTINE: return ComplianceReport.ReportPriority.ROUTINE;
            default: return ComplianceReport.ReportPriority.MEDIUM;
        }
    }

    /**
     * Get CSS class for priority styling
     */
    public String getCssClass() {
        switch (this) {
            case CRITICAL: return "priority-critical";
            case HIGH: return "priority-high";
            case MEDIUM: return "priority-medium";
            case LOW: return "priority-low";
            case ROUTINE: return "priority-routine";
            default: return "priority-default";
        }
    }

    /**
     * Get icon class for priority display
     */
    public String getIconClass() {
        switch (this) {
            case CRITICAL: return "fa-exclamation-triangle text-danger";
            case HIGH: return "fa-exclamation-circle text-warning";
            case MEDIUM: return "fa-info-circle text-info";
            case LOW: return "fa-arrow-down text-secondary";
            case ROUTINE: return "fa-calendar text-muted";
            default: return "fa-file";
        }
    }
}
