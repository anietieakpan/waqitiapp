package com.waqiti.payment.domain;

/**
 * Represents the possible states of a scheduled payment
 */
public enum ScheduledPaymentStatus {
    ACTIVE,     // Scheduled payment is active
    PAUSED,     // Scheduled payment is paused
    CANCELED,   // Scheduled payment has been canceled
    COMPLETED   // Scheduled payment has completed all executions
}
