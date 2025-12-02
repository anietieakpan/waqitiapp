package com.waqiti.payroll.domain;

/**
 * Payment Status Enum
 *
 * Represents the lifecycle status of an individual payroll payment.
 *
 * Status Flow:
 * PENDING → PROCESSING → COMPLETED → SETTLED
 *                     → FAILED → RETRY_PENDING
 *                     → REJECTED
 */
public enum PaymentStatus {

    /**
     * Payment queued but not yet initiated
     */
    PENDING("Pending"),

    /**
     * Payment currently being processed by bank
     */
    PROCESSING("Processing"),

    /**
     * Payment successfully sent to bank
     */
    COMPLETED("Completed"),

    /**
     * Payment settled and confirmed by bank
     */
    SETTLED("Settled"),

    /**
     * Payment failed - will be retried
     */
    FAILED("Failed"),

    /**
     * Payment rejected by bank - will not retry
     */
    REJECTED("Rejected"),

    /**
     * Payment scheduled for retry
     */
    RETRY_PENDING("Retry Pending"),

    /**
     * Payment cancelled by user/system
     */
    CANCELLED("Cancelled"),

    /**
     * Payment on hold pending review
     */
    ON_HOLD("On Hold"),

    /**
     * Payment reversed/refunded
     */
    REVERSED("Reversed");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == SETTLED || this == REJECTED ||
               this == CANCELLED || this == REVERSED;
    }

    public boolean isSuccessful() {
        return this == COMPLETED || this == SETTLED;
    }

    public boolean canRetry() {
        return this == FAILED || this == RETRY_PENDING;
    }
}
