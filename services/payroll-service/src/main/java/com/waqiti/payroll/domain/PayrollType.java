package com.waqiti.payroll.domain;

/**
 * Payroll Type Enum
 *
 * Categorizes the type of payroll being processed for tax and compliance purposes.
 */
public enum PayrollType {

    /**
     * Regular bi-weekly or monthly salary payments
     */
    REGULAR("Regular Payroll"),

    /**
     * Hourly wage payments with overtime calculations
     */
    HOURLY("Hourly Wages"),

    /**
     * One-time or periodic bonus payments
     */
    BONUS("Bonus Payment"),

    /**
     * Sales commission payments
     */
    COMMISSION("Commission Payment"),

    /**
     * Independent contractor payments (1099)
     */
    CONTRACTOR("Contractor Payment"),

    /**
     * Overtime-only payments
     */
    OVERTIME("Overtime Payment"),

    /**
     * Severance or termination payments
     */
    SEVERANCE("Severance Payment"),

    /**
     * Benefits disbursement
     */
    BENEFITS("Benefits Payment"),

    /**
     * Holiday or PTO payout
     */
    HOLIDAY("Holiday Pay"),

    /**
     * Reimbursement for expenses
     */
    REIMBURSEMENT("Expense Reimbursement"),

    /**
     * Adjustment or correction payment
     */
    ADJUSTMENT("Payroll Adjustment"),

    /**
     * Back pay for missed payments
     */
    BACKPAY("Back Pay");

    private final String displayName;

    PayrollType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresTaxWithholding() {
        // Contractors and reimbursements typically don't have tax withholding
        return this != CONTRACTOR && this != REIMBURSEMENT;
    }

    public boolean isW2Employee() {
        return this != CONTRACTOR;
    }

    public boolean is1099Contractor() {
        return this == CONTRACTOR;
    }
}
