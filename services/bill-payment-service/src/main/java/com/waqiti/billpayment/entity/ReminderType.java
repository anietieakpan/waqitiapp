package com.waqiti.billpayment.entity;

/**
 * Types of bill reminders
 */
public enum ReminderType {
    /**
     * Reminder sent 7 days before due date
     */
    DUE_IN_7_DAYS,

    /**
     * Reminder sent 3 days before due date
     */
    DUE_IN_3_DAYS,

    /**
     * Reminder sent 1 day before due date
     */
    DUE_TOMORROW,

    /**
     * Reminder sent on due date
     */
    DUE_TODAY,

    /**
     * Reminder sent when bill is overdue
     */
    OVERDUE,

    /**
     * Custom reminder at user-specified time
     */
    CUSTOM
}
