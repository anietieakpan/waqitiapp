package com.waqiti.common.domain;

/**
 * AML (Anti-Money Laundering) Screening Status
 *
 * Represents the status of AML compliance screening.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
public enum AMLScreeningStatus {

    PENDING("AML screening queued"),
    IN_PROGRESS("AML screening in progress"),
    CLEARED("Passed AML screening - no hits"),
    HIT_DETECTED("Potential match found - requires review"),
    UNDER_REVIEW("Manual review in progress"),
    APPROVED("Approved after review"),
    REJECTED("Rejected - failed AML screening"),
    EXPIRED("Screening expired - needs re-screening"),
    ERROR("Screening error occurred");

    private final String description;

    AMLScreeningStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == CLEARED || this == APPROVED || this == REJECTED;
    }

    public boolean requiresAction() {
        return this == HIT_DETECTED || this == UNDER_REVIEW || this == ERROR;
    }
}
