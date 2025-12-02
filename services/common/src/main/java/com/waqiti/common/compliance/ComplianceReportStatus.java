package com.waqiti.common.compliance;

/**
 * Enumeration of compliance report statuses in the Waqiti compliance workflow
 * 
 * Represents the lifecycle states of a compliance report from initial creation
 * through final filing with regulatory authorities.
 */
public enum ComplianceReportStatus {

    /**
     * Report has been created but work has not started
     */
    DRAFT("Draft", "Report created but not yet started", false, false, false),

    /**
     * Report is actively being worked on
     */
    IN_PROGRESS("In Progress", "Report is being actively developed", false, false, false),

    /**
     * Report is pending review by compliance team
     */
    PENDING_REVIEW("Pending Review", "Report submitted for compliance review", false, false, false),

    /**
     * Report is under review by compliance team
     */
    UNDER_REVIEW("Under Review", "Report is being reviewed by compliance team", false, false, false),

    /**
     * Report has been reviewed and requires revisions
     */
    REQUIRES_REVISION("Requires Revision", "Report needs revisions before approval", false, false, false),

    /**
     * Report is pending legal review
     */
    PENDING_LEGAL_REVIEW("Pending Legal Review", "Report awaiting legal team review", false, false, false),

    /**
     * Report is under legal review
     */
    UNDER_LEGAL_REVIEW("Under Legal Review", "Report is being reviewed by legal team", false, false, false),

    /**
     * Report is pending management approval
     */
    PENDING_APPROVAL("Pending Approval", "Report awaiting management approval", false, false, false),

    /**
     * Report has been approved internally
     */
    APPROVED("Approved", "Report approved and ready for submission", true, false, false),

    /**
     * Report is being prepared for submission
     */
    PREPARING_SUBMISSION("Preparing Submission", "Report being prepared for regulatory submission", true, false, false),

    /**
     * Report has been submitted to regulatory authority
     */
    SUBMITTED("Submitted", "Report submitted to regulatory authority", true, true, false),

    /**
     * Report submission has been acknowledged by regulatory authority
     */
    ACKNOWLEDGED("Acknowledged", "Report submission acknowledged by regulator", true, true, false),

    /**
     * Report has been filed and accepted by regulatory authority
     */
    FILED("Filed", "Report successfully filed with regulatory authority", true, true, true),

    /**
     * Report was rejected by regulatory authority
     */
    REJECTED("Rejected", "Report rejected by regulatory authority", false, true, false),

    /**
     * Report submission is under regulatory review
     */
    UNDER_REGULATORY_REVIEW("Under Regulatory Review", "Report is being reviewed by regulatory authority", true, true, false),

    /**
     * Additional information requested by regulatory authority
     */
    INFO_REQUESTED("Info Requested", "Regulatory authority requested additional information", false, true, false),

    /**
     * Report was cancelled before submission
     */
    CANCELLED("Cancelled", "Report was cancelled before submission", false, false, false),

    /**
     * Report is on hold pending external factors
     */
    ON_HOLD("On Hold", "Report processing is temporarily suspended", false, false, false),

    /**
     * Report is archived after completion
     */
    ARCHIVED("Archived", "Report has been archived for retention", true, true, true),

    /**
     * Report processing failed due to system error
     */
    FAILED("Failed", "Report processing failed due to system error", false, false, false),

    /**
     * Report is overdue for filing
     */
    OVERDUE("Overdue", "Report has passed its filing deadline", false, false, false),

    /**
     * Report is scheduled for future processing
     */
    SCHEDULED("Scheduled", "Report is scheduled for future processing", false, false, false),

    /**
     * Report has been completed (alias for FILED for backward compatibility)
     */
    COMPLETED("Completed", "Report has been completed and filed", true, true, true),

    /**
     * Report was not found in the system
     */
    NOT_FOUND("Not Found", "Report could not be found in the system", false, false, false);

    private final String displayName;
    private final String description;
    private final boolean approved;
    private final boolean submitted;
    private final boolean completed;

    ComplianceReportStatus(String displayName, String description, 
                          boolean approved, boolean submitted, boolean completed) {
        this.displayName = displayName;
        this.description = description;
        this.approved = approved;
        this.submitted = submitted;
        this.completed = completed;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public boolean isApproved() { return approved; }
    public boolean isSubmitted() { return submitted; }
    public boolean isCompleted() { return completed; }

    /**
     * Check if status represents a draft or work-in-progress state
     */
    public boolean isDraftOrInProgress() {
        return this == DRAFT || 
               this == IN_PROGRESS || 
               this == REQUIRES_REVISION ||
               this == SCHEDULED;
    }

    /**
     * Check if status represents a review state
     */
    public boolean isInReview() {
        return this == PENDING_REVIEW ||
               this == UNDER_REVIEW ||
               this == PENDING_LEGAL_REVIEW ||
               this == UNDER_LEGAL_REVIEW ||
               this == PENDING_APPROVAL ||
               this == UNDER_REGULATORY_REVIEW;
    }

    /**
     * Check if status represents an error or problem state
     */
    public boolean isErrorState() {
        return this == REJECTED ||
               this == FAILED ||
               this == CANCELLED ||
               this == OVERDUE;
    }

    /**
     * Check if status represents a final state
     */
    public boolean isFinalState() {
        return this == FILED ||
               this == ARCHIVED ||
               this == CANCELLED ||
               this == REJECTED;
    }

    /**
     * Check if status allows editing
     */
    public boolean allowsEditing() {
        return this == DRAFT ||
               this == IN_PROGRESS ||
               this == REQUIRES_REVISION ||
               this == INFO_REQUESTED;
    }

    /**
     * Check if status requires action
     */
    public boolean requiresAction() {
        return this == REQUIRES_REVISION ||
               this == INFO_REQUESTED ||
               this == OVERDUE ||
               this == FAILED ||
               this == ON_HOLD;
    }

    /**
     * Get the next logical status in the workflow
     */
    public ComplianceReportStatus getNextStatus() {
        switch (this) {
            case DRAFT:
                return IN_PROGRESS;
            case IN_PROGRESS:
                return PENDING_REVIEW;
            case PENDING_REVIEW:
                return UNDER_REVIEW;
            case UNDER_REVIEW:
                return PENDING_APPROVAL;
            case REQUIRES_REVISION:
                return IN_PROGRESS;
            case PENDING_LEGAL_REVIEW:
                return UNDER_LEGAL_REVIEW;
            case UNDER_LEGAL_REVIEW:
                return PENDING_APPROVAL;
            case PENDING_APPROVAL:
                return APPROVED;
            case APPROVED:
                return PREPARING_SUBMISSION;
            case PREPARING_SUBMISSION:
                return SUBMITTED;
            case SUBMITTED:
                return ACKNOWLEDGED;
            case ACKNOWLEDGED:
                return FILED;
            case FILED:
                return ARCHIVED;
            case REJECTED:
                return REQUIRES_REVISION;
            case INFO_REQUESTED:
                return IN_PROGRESS;
            case UNDER_REGULATORY_REVIEW:
                return FILED;
            case SCHEDULED:
                return DRAFT;
            default:
                return this; // No next status defined
        }
    }

    /**
     * Get possible transition statuses from current status
     */
    public ComplianceReportStatus[] getPossibleTransitions() {
        switch (this) {
            case DRAFT:
                return new ComplianceReportStatus[]{IN_PROGRESS, CANCELLED, ON_HOLD};
            case IN_PROGRESS:
                return new ComplianceReportStatus[]{PENDING_REVIEW, DRAFT, ON_HOLD, CANCELLED};
            case PENDING_REVIEW:
                return new ComplianceReportStatus[]{UNDER_REVIEW, REQUIRES_REVISION};
            case UNDER_REVIEW:
                return new ComplianceReportStatus[]{PENDING_APPROVAL, PENDING_LEGAL_REVIEW, REQUIRES_REVISION};
            case REQUIRES_REVISION:
                return new ComplianceReportStatus[]{IN_PROGRESS, CANCELLED};
            case PENDING_LEGAL_REVIEW:
                return new ComplianceReportStatus[]{UNDER_LEGAL_REVIEW, REQUIRES_REVISION};
            case UNDER_LEGAL_REVIEW:
                return new ComplianceReportStatus[]{PENDING_APPROVAL, REQUIRES_REVISION};
            case PENDING_APPROVAL:
                return new ComplianceReportStatus[]{APPROVED, REQUIRES_REVISION};
            case APPROVED:
                return new ComplianceReportStatus[]{PREPARING_SUBMISSION, REQUIRES_REVISION};
            case PREPARING_SUBMISSION:
                return new ComplianceReportStatus[]{SUBMITTED, FAILED};
            case SUBMITTED:
                return new ComplianceReportStatus[]{ACKNOWLEDGED, UNDER_REGULATORY_REVIEW, REJECTED};
            case ACKNOWLEDGED:
                return new ComplianceReportStatus[]{FILED, UNDER_REGULATORY_REVIEW, INFO_REQUESTED};
            case REJECTED:
                return new ComplianceReportStatus[]{REQUIRES_REVISION, CANCELLED};
            case UNDER_REGULATORY_REVIEW:
                return new ComplianceReportStatus[]{FILED, INFO_REQUESTED, REJECTED};
            case INFO_REQUESTED:
                return new ComplianceReportStatus[]{IN_PROGRESS, SUBMITTED};
            case ON_HOLD:
                return new ComplianceReportStatus[]{IN_PROGRESS, CANCELLED};
            case OVERDUE:
                return new ComplianceReportStatus[]{SUBMITTED, CANCELLED};
            case SCHEDULED:
                return new ComplianceReportStatus[]{DRAFT, CANCELLED};
            case FILED:
                return new ComplianceReportStatus[]{ARCHIVED};
            default:
                return new ComplianceReportStatus[]{}; // Final states have no transitions
        }
    }

    /**
     * Get status by display name
     */
    public static ComplianceReportStatus fromDisplayName(String displayName) {
        if (displayName == null) return null;
        
        for (ComplianceReportStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Get CSS class for status styling
     */
    public String getCssClass() {
        if (isCompleted()) return "status-success";
        if (isErrorState()) return "status-danger";
        if (isInReview()) return "status-warning";
        if (isDraftOrInProgress()) return "status-info";
        return "status-secondary";
    }

    /**
     * Get icon class for status display
     */
    public String getIconClass() {
        switch (this) {
            case DRAFT:
            case IN_PROGRESS:
                return "fa-edit";
            case PENDING_REVIEW:
            case UNDER_REVIEW:
            case PENDING_LEGAL_REVIEW:
            case UNDER_LEGAL_REVIEW:
                return "fa-search";
            case PENDING_APPROVAL:
                return "fa-clock";
            case APPROVED:
                return "fa-check";
            case PREPARING_SUBMISSION:
                return "fa-upload";
            case SUBMITTED:
            case ACKNOWLEDGED:
                return "fa-paper-plane";
            case FILED:
                return "fa-check-circle";
            case REJECTED:
                return "fa-times-circle";
            case CANCELLED:
                return "fa-ban";
            case FAILED:
                return "fa-exclamation-triangle";
            case OVERDUE:
                return "fa-clock";
            case ON_HOLD:
                return "fa-pause";
            case ARCHIVED:
                return "fa-archive";
            case SCHEDULED:
                return "fa-calendar";
            default:
                return "fa-file";
        }
    }
}