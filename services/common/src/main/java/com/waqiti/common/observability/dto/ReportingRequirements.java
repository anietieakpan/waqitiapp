package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enterprise compliance reporting requirements for violations and incidents
 * Defines mandatory notifications, deadlines, and regulatory obligations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportingRequirements {
    
    /**
     * Whether immediate reporting is required (within hours)
     */
    private boolean isImmediateReportingRequired;
    
    /**
     * Deadline for mandatory notifications to regulatory authorities
     */
    private LocalDateTime notificationDeadline;
    
    /**
     * List of required notifications and recipients
     */
    private List<String> requiredNotifications;
    
    /**
     * List of regulatory frameworks that require notification
     */
    private List<String> applicableRegulations;
    
    /**
     * Priority level for reporting (CRITICAL, HIGH, MEDIUM, LOW)
     */
    private String reportingPriority;
    
    /**
     * Whether external authorities must be notified
     */
    private boolean requiresExternalNotification;
    
    /**
     * Specific reporting format required (JSON, XML, PDF, etc.)
     */
    private String reportingFormat;
    
    /**
     * Languages required for reporting (multi-jurisdiction compliance)
     */
    private List<String> reportingLanguages;
    
    /**
     * Whether legal review is required before submission
     */
    private boolean requiresLegalReview;
    
    /**
     * Escalation path for reporting failures
     */
    private String escalationPath;
    
    /**
     * Additional metadata for compliance tracking
     */
    private String complianceNotes;
    
    /**
     * Auto-generated reminder dates
     */
    private List<LocalDateTime> reminderSchedule;
    
    /**
     * Create immediate reporting requirements
     */
    public static ReportingRequirements createImmediateReporting(String regulation, List<String> recipients) {
        return ReportingRequirements.builder()
            .isImmediateReportingRequired(true)
            .notificationDeadline(LocalDateTime.now().plusHours(2))
            .requiredNotifications(recipients)
            .applicableRegulations(List.of(regulation))
            .reportingPriority("CRITICAL")
            .requiresExternalNotification(true)
            .reportingFormat("JSON")
            .requiresLegalReview(false) // Skip for urgent cases
            .escalationPath("EXECUTIVE_NOTIFICATION")
            .build();
    }
    
    /**
     * Create standard regulatory reporting requirements
     */
    public static ReportingRequirements createStandardReporting(String regulation, LocalDateTime deadline) {
        return ReportingRequirements.builder()
            .isImmediateReportingRequired(false)
            .notificationDeadline(deadline)
            .applicableRegulations(List.of(regulation))
            .reportingPriority("HIGH")
            .requiresExternalNotification(true)
            .reportingFormat("PDF")
            .requiresLegalReview(true)
            .escalationPath("COMPLIANCE_TEAM")
            .build();
    }
    
    /**
     * Create internal-only reporting requirements
     */
    public static ReportingRequirements createInternalReporting() {
        return ReportingRequirements.builder()
            .isImmediateReportingRequired(false)
            .notificationDeadline(LocalDateTime.now().plusDays(7))
            .reportingPriority("MEDIUM")
            .requiresExternalNotification(false)
            .reportingFormat("JSON")
            .requiresLegalReview(false)
            .escalationPath("INTERNAL_REVIEW")
            .build();
    }
    
    /**
     * Check if reporting deadline has passed
     */
    public boolean isOverdue() {
        return notificationDeadline != null && LocalDateTime.now().isAfter(notificationDeadline);
    }
    
    /**
     * Get remaining time until deadline in hours
     */
    public long getHoursUntilDeadline() {
        if (notificationDeadline == null) {
            return Long.MAX_VALUE;
        }
        
        return java.time.Duration.between(LocalDateTime.now(), notificationDeadline).toHours();
    }
    
    /**
     * Check if urgent action is required (within 24 hours)
     */
    public boolean isUrgent() {
        return isImmediateReportingRequired || getHoursUntilDeadline() <= 24;
    }
    
    // Explicit getters to handle Lombok processing issues
    public boolean isImmediateReportingRequired() {
        return isImmediateReportingRequired;
    }
    
    public LocalDateTime getNotificationDeadline() {
        return notificationDeadline;
    }
    
    public List<String> getRequiredNotifications() {
        return requiredNotifications;
    }
    
    public List<String> getApplicableRegulations() {
        return applicableRegulations;
    }
    
    public String getReportingPriority() {
        return reportingPriority;
    }
    
    public boolean requiresExternalNotification() {
        return requiresExternalNotification;
    }
    
    public String getReportingFormat() {
        return reportingFormat;
    }
    
    public List<String> getReportingLanguages() {
        return reportingLanguages;
    }
    
    public boolean requiresLegalReview() {
        return requiresLegalReview;
    }
    
    public String getEscalationPath() {
        return escalationPath;
    }
    
    public String getComplianceNotes() {
        return complianceNotes;
    }
    
    public List<LocalDateTime> getReminderSchedule() {
        return reminderSchedule;
    }
    
    // Removed custom builder - using Lombok @Builder instead
    /* Commenting out custom builder to avoid conflicts with Lombok
    public static class ReportingRequirementsBuilder {
        private boolean isImmediateReportingRequired;
        private LocalDateTime notificationDeadline;
        private List<String> requiredNotifications;
        private List<String> applicableRegulations;
        private String reportingPriority;
        private boolean requiresExternalNotification;
        private String reportingFormat;
        private List<String> reportingLanguages;
        private boolean requiresLegalReview;
        private String escalationPath;
        private String complianceNotes;
        private List<LocalDateTime> reminderSchedule;
        
        public ReportingRequirementsBuilder isImmediateReportingRequired(boolean isImmediateReportingRequired) { this.isImmediateReportingRequired = isImmediateReportingRequired; return this; }
        public ReportingRequirementsBuilder notificationDeadline(LocalDateTime notificationDeadline) { this.notificationDeadline = notificationDeadline; return this; }
        public ReportingRequirementsBuilder requiredNotifications(List<String> requiredNotifications) { this.requiredNotifications = requiredNotifications; return this; }
        public ReportingRequirementsBuilder applicableRegulations(List<String> applicableRegulations) { this.applicableRegulations = applicableRegulations; return this; }
        public ReportingRequirementsBuilder reportingPriority(String reportingPriority) { this.reportingPriority = reportingPriority; return this; }
        public ReportingRequirementsBuilder requiresExternalNotification(boolean requiresExternalNotification) { this.requiresExternalNotification = requiresExternalNotification; return this; }
        public ReportingRequirementsBuilder reportingFormat(String reportingFormat) { this.reportingFormat = reportingFormat; return this; }
        public ReportingRequirementsBuilder reportingLanguages(List<String> reportingLanguages) { this.reportingLanguages = reportingLanguages; return this; }
        public ReportingRequirementsBuilder requiresLegalReview(boolean requiresLegalReview) { this.requiresLegalReview = requiresLegalReview; return this; }
        public ReportingRequirementsBuilder escalationPath(String escalationPath) { this.escalationPath = escalationPath; return this; }
        public ReportingRequirementsBuilder complianceNotes(String complianceNotes) { this.complianceNotes = complianceNotes; return this; }
        public ReportingRequirementsBuilder reminderSchedule(List<LocalDateTime> reminderSchedule) { this.reminderSchedule = reminderSchedule; return this; }
        
        public ReportingRequirements build() {
            return new ReportingRequirements(isImmediateReportingRequired, notificationDeadline, requiredNotifications, applicableRegulations, reportingPriority, requiresExternalNotification, reportingFormat, reportingLanguages, requiresLegalReview, escalationPath, complianceNotes, reminderSchedule);
        }
    }
    */
}