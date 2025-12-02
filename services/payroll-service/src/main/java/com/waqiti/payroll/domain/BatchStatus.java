package com.waqiti.payroll.domain;

/**
 * Batch Status Enum
 *
 * Represents the lifecycle status of a payroll batch processing run.
 *
 * Status Flow:
 * PENDING → PROCESSING → COMPLETED/PARTIALLY_COMPLETED/FAILED
 *                     → COMPLETED_WITH_REVIEW (if compliance issues)
 */
public enum BatchStatus {

    /**
     * Batch created but not yet started processing
     */
    PENDING("Pending Processing"),

    /**
     * Batch currently being processed
     */
    PROCESSING("In Progress"),

    /**
     * All payments successfully processed
     */
    COMPLETED("Completed Successfully"),

    /**
     * All payments processed but requires manual review due to compliance issues
     */
    COMPLETED_WITH_REVIEW("Completed - Review Required"),

    /**
     * Some payments succeeded, some failed
     */
    PARTIALLY_COMPLETED("Partially Completed"),

    /**
     * All payments failed
     */
    FAILED("Failed"),

    /**
     * Batch cancelled by user/system
     */
    CANCELLED("Cancelled"),

    /**
     * Batch on hold pending approval
     */
    ON_HOLD("On Hold"),

    /**
     * Batch being retried after failure
     */
    RETRYING("Retrying");

    private final String displayName;

    BatchStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_REVIEW ||
               this == FAILED || this == CANCELLED;
    }

    public boolean isSuccessful() {
        return this == COMPLETED || this == COMPLETED_WITH_REVIEW ||
               this == PARTIALLY_COMPLETED;
    }
}
