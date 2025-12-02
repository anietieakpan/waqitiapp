package com.waqiti.bnpl.domain.enums;

/**
 * Status of a BNPL installment
 */
public enum InstallmentStatus {
    SCHEDULED,        // Future installment
    DUE,             // Currently due for payment
    PARTIALLY_PAID,  // Part of the amount has been paid
    PAID,            // Fully paid
    OVERDUE,         // Past due date without full payment
    WAIVED           // Installment was waived
}