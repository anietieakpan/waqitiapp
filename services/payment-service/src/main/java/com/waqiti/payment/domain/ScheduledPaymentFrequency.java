package com.waqiti.payment.domain;

/**
 * Represents the frequency of a scheduled payment
 */
public enum ScheduledPaymentFrequency {
    ONE_TIME,   // Once only
    DAILY,      // Every day
    WEEKLY,     // Every week
    BIWEEKLY,   // Every two weeks
    MONTHLY,    // Every month
    QUARTERLY,  // Every three months
    YEARLY      // Every year
}