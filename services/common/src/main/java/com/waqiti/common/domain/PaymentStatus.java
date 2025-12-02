package com.waqiti.common.domain;

/**
 * Payment Processing Status Enum
 *
 * Represents all possible states in the payment lifecycle.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
public enum PaymentStatus {

    // Initial States
    PENDING("Payment initiated, awaiting processing"),
    PROCESSING("Payment is being processed"),

    // ACH Specific States
    SCHEDULED("ACH payment scheduled for future date"),
    SUBMITTED("ACH batch submitted to clearing house"),

    // Authorization States
    AUTHORIZED("Payment authorized, not yet captured"),
    PARTIALLY_COMPLETED("Partial payment completed"),

    // Success States
    COMPLETED("Payment successfully completed"),
    SETTLED("Payment settled with merchant"),

    // Failure States
    FAILED("Payment processing failed"),
    DECLINED("Payment declined by issuer"),
    REJECTED("Payment rejected"),
    CANCELLED("Payment cancelled by user"),
    EXPIRED("Payment authorization expired"),

    // Refund/Reversal States
    REFUNDED("Payment refunded"),
    PARTIALLY_REFUNDED("Payment partially refunded"),
    REVERSED("Payment reversed"),

    // Hold States
    ON_HOLD("Payment on hold for review"),
    FROZEN("Payment frozen due to compliance"),

    // Review States
    UNDER_REVIEW("Payment under manual review"),
    PENDING_VERIFICATION("Awaiting verification");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED ||
               this == DECLINED || this == REFUNDED || this == SETTLED;
    }

    public boolean isSuccessful() {
        return this == COMPLETED || this == SETTLED || this == PARTIALLY_COMPLETED;
    }

    public boolean isFailed() {
        return this == FAILED || this == DECLINED || this == REJECTED || this == EXPIRED;
    }
}
