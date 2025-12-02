package com.waqiti.payment.reversal.dto;

/**
 * Reasons for payment reversal
 */
public enum ReversalReason {
    CUSTOMER_REQUEST,
    FRAUD_DETECTED,
    DUPLICATE_TRANSACTION,
    TECHNICAL_ERROR,
    INSUFFICIENT_FUNDS,
    ACCOUNT_CLOSURE,
    REGULATORY_REQUIREMENT,
    DISPUTE,
    CHARGEBACK,
    MERCHANT_REQUEST
}
