package com.waqiti.bnpl.domain.enums;

/**
 * Type of BNPL transaction
 */
public enum TransactionType {
    PURCHASE,            // Initial purchase transaction
    DOWN_PAYMENT,        // Down payment for the BNPL plan
    INSTALLMENT_PAYMENT, // Regular installment payment
    LATE_FEE,           // Late fee charge
    REFUND,             // Refund transaction
    ADJUSTMENT,         // Manual adjustment
    PENALTY,            // Penalty charge
    INTEREST            // Interest charge
}