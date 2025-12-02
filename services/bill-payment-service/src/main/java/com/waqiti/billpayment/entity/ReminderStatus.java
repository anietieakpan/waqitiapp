package com.waqiti.billpayment.entity;

/**
 * Status values for bill reminders
 */
public enum ReminderStatus {
    /**
     * Reminder scheduled but not yet sent
     */
    PENDING,

    /**
     * Reminder successfully sent
     */
    SENT,

    /**
     * Reminder failed to send
     */
    FAILED,

    /**
     * Reminder cancelled (bill paid or deleted)
     */
    CANCELLED
}
