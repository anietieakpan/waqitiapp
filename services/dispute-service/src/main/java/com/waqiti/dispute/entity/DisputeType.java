package com.waqiti.dispute.entity;

/**
 * Dispute type enumeration
 */
public enum DisputeType {
    FRAUD("Fraudulent transaction claim"),
    UNAUTHORIZED("Unauthorized transaction"),
    CHARGEBACK("Payment provider chargeback"),
    SERVICE_ISSUE("Service not received or defective"),
    DUPLICATE_CHARGE("Duplicate transaction charge"),
    WRONG_AMOUNT("Incorrect amount charged"),
    REFUND_NOT_RECEIVED("Refund not processed"),
    ACCOUNT_TAKEOVER("Account compromise claim"),
    MERCHANT_ERROR("Merchant processing error"),
    OTHER("Other dispute reason");

    private final String description;

    DisputeType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
