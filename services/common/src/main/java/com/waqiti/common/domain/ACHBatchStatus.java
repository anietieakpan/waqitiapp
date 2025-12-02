package com.waqiti.common.domain;

/**
 * ACH Batch Processing Status
 *
 * Represents the lifecycle states of an ACH batch file.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
public enum ACHBatchStatus {

    DRAFT("Batch is being created"),
    PENDING("Batch created, awaiting submission"),
    VALIDATING("Batch undergoing validation"),
    VALIDATED("Batch passed validation"),
    SCHEDULED("Batch scheduled for submission"),
    SUBMITTED("Batch submitted to clearing house"),
    PROCESSING("Batch being processed by NACHA"),
    PROCESSING_FAILED("Batch processing failed"),
    COMPLETED("Batch successfully processed"),
    PARTIALLY_COMPLETED("Batch partially processed"),
    FAILED("Batch failed completely"),
    CANCELLED("Batch cancelled before submission");

    private final String description;

    ACHBatchStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == PARTIALLY_COMPLETED;
    }
}
