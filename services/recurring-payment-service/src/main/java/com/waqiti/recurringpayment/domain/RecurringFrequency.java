package com.waqiti.recurringpayment.domain;

public enum RecurringFrequency {
    DAILY,      // Every day
    WEEKLY,     // Every week (specific day of week)
    BIWEEKLY,   // Every two weeks
    MONTHLY,    // Every month (specific day or pattern)
    QUARTERLY,  // Every 3 months
    YEARLY      // Every year
}