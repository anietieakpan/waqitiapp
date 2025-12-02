package com.waqiti.lending.domain.enums;

/**
 * Loan Account Status
 */
public enum LoanStatus {
    PENDING,            // Loan approved but not yet disbursed
    ACTIVE,             // Loan is active and current
    CURRENT,            // Loan payments are current
    DELINQUENT,         // Loan is past due (1-89 days)
    DEFAULT,            // Loan is in default (90+ days)
    CHARGED_OFF,        // Loan written off as loss
    PAID_OFF,           // Loan fully paid
    CLOSED,             // Loan account closed
    CANCELLED,          // Loan cancelled before disbursement
    IN_FORBEARANCE,     // Temporary payment suspension
    IN_MODIFICATION,    // Under modification/restructuring
    IN_COLLECTIONS,     // Sent to collections
    IN_BANKRUPTCY,      // Borrower in bankruptcy
    REFINANCED,         // Loan refinanced (closed)
    SETTLED,            // Settled for less than full amount
    REPOSSESSED         // Collateral repossessed
}
