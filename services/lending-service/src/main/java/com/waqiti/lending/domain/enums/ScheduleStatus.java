package com.waqiti.lending.domain.enums;

/**
 * Loan Schedule Status
 */
public enum ScheduleStatus {
    SCHEDULED,          // Future scheduled payment
    DUE,                // Payment is due now
    OVERDUE,            // Payment past due
    PAID,               // Payment received
    PARTIALLY_PAID,     // Partial payment received
    SKIPPED,            // Payment skipped (forbearance/modification)
    DEFERRED            // Payment deferred to later date
}
