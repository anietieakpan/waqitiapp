package com.waqiti.recurringpayment.domain;

public enum RecurringStatus {
    ACTIVE,     // Recurring payment is active and processing
    PAUSED,     // Temporarily paused by user or system
    CANCELLED,  // Permanently cancelled
    COMPLETED,  // All payments completed (reached end date or max occurrences)
    EXPIRED     // Start date has passed without activation
}