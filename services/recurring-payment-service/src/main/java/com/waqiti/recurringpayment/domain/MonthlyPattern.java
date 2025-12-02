package com.waqiti.recurringpayment.domain;

public enum MonthlyPattern {
    SAME_DATE,  // Same day of month (e.g., 15th of every month)
    LAST_DAY,   // Last day of the month
    FIRST_WEEKDAY, // First weekday of the month
    LAST_WEEKDAY   // Last weekday of the month
}