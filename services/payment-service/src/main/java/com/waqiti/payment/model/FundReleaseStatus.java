package com.waqiti.payment.model;

public enum FundReleaseStatus {
    PENDING,
    SCHEDULED,
    BATCHED,
    PROCESSING,
    COMPLETED,
    FAILED,
    MANUAL_REVIEW,
    CANCELLED,
    REFUNDED
}