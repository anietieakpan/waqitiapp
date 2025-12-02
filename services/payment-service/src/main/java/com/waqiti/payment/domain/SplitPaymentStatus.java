package com.waqiti.payment.domain;

/**
 * Represents the possible states of a split payment
 */
public enum SplitPaymentStatus {
    ACTIVE,     // Split payment is active
    COMPLETED,  // Split payment is completed (all participants have paid)
    CANCELED,   // Split payment has been canceled
    EXPIRED     // Split payment has expired
}