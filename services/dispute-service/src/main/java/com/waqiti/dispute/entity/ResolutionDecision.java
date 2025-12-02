package com.waqiti.dispute.entity;

/**
 * Resolution decision enumeration
 */
public enum ResolutionDecision {
    APPROVED_FULL_REFUND("Full refund approved"),
    APPROVED_PARTIAL_REFUND("Partial refund approved"),
    REJECTED("Dispute rejected"),
    MERCHANT_LIABLE("Merchant found liable"),
    CUSTOMER_LIABLE("Customer found liable"),
    SPLIT_LIABILITY("Liability split between parties"),
    WITHDRAWN("Customer withdrew dispute"),
    EXPIRED("Dispute expired"),
    CHARGEBACK_WON("Chargeback won by merchant"),
    CHARGEBACK_LOST("Chargeback lost to customer"),
    FAVOR_CUSTOMER();

    private final String description;

    ResolutionDecision(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRefundDecision() {
        return this == APPROVED_FULL_REFUND || this == APPROVED_PARTIAL_REFUND;
    }
}
