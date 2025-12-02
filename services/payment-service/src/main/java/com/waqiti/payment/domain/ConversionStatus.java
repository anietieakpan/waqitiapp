package com.waqiti.payment.domain;

public enum ConversionStatus {
    INITIATED,
    RATE_FETCHED,
    RATE_LOCKED,
    EXECUTING,
    EXECUTED,
    COMPLETED,
    FAILED,
    CANCELLED
}
