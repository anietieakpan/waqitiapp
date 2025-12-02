package com.waqiti.payment.dispute.dto;

/**
 * Dispute reasons
 */
public enum DisputeReason {
    UNAUTHORIZED_TRANSACTION,
    DUPLICATE_CHARGE,
    INCORRECT_AMOUNT,
    PRODUCT_NOT_RECEIVED,
    PRODUCT_DEFECTIVE,
    SERVICE_NOT_AS_DESCRIBED,
    CANCELLED_SUBSCRIPTION,
    BILLING_ERROR,
    FRAUD,
    OTHER
}
