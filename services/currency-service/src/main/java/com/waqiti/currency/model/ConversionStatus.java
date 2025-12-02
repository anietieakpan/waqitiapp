package com.waqiti.currency.model;

/**
 * Currency conversion status
 */
public enum ConversionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED,
    COMPLIANCE_HOLD,
    TREASURY_REVIEW,
    PENDING_EXCHANGE_RATE,
    UNSUPPORTED_CURRENCY_PAIR,
    CANCELLED
}
