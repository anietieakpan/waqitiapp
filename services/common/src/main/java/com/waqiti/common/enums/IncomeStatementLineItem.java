package com.waqiti.common.enums;

/**
 * Income Statement Line Items for Financial Reporting
 *
 * Represents all standard line items in a financial income statement
 * following GAAP (Generally Accepted Accounting Principles).
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
public enum IncomeStatementLineItem {

    // Revenue Items
    REVENUE("Revenue", "Total revenue from operations", true),
    SALES_REVENUE("Sales Revenue", "Revenue from product/service sales", true),
    SERVICE_REVENUE("Service Revenue", "Revenue from services", true),
    INTEREST_INCOME("Interest Income", "Income from interest", true),
    COMMISSION_INCOME("Commission Income", "Commission and fees", true),
    OTHER_INCOME("Other Income", "Miscellaneous income", true),

    // Cost of Goods Sold / Direct Costs
    COST_OF_REVENUE("Cost of Revenue", "Direct costs of providing service", false),
    COST_OF_GOODS_SOLD("Cost of Goods Sold", "COGS", false),

    // Gross Profit
    GROSS_PROFIT("Gross Profit", "Revenue minus COGS", true),

    // Operating Expenses
    OPERATING_EXPENSES("Operating Expenses", "Total operating expenses", false),
    SALARIES_EXPENSE("Salaries and Wages", "Employee compensation", false),
    RENT_EXPENSE("Rent Expense", "Facility rental costs", false),
    UTILITIES_EXPENSE("Utilities Expense", "Utilities costs", false),
    MARKETING_EXPENSE("Marketing Expense", "Marketing and advertising", false),
    DEPRECIATION_EXPENSE("Depreciation", "Asset depreciation", false),
    AMORTIZATION_EXPENSE("Amortization", "Intangible asset amortization", false),
    INSURANCE_EXPENSE("Insurance", "Insurance premiums", false),
    TAX_EXPENSE("Tax Expense", "Income tax expense", false),
    OPERATIONAL_EXPENSE("Operational Expense", "General operational costs", false),

    // Operating Income
    OPERATING_INCOME("Operating Income", "Income from operations", true),
    EBITDA("EBITDA", "Earnings before interest, taxes, depreciation, amortization", true),

    // Non-Operating Items
    INTEREST_EXPENSE("Interest Expense", "Interest on debt", false),
    GAINS_LOSSES("Gains/Losses", "Non-operating gains and losses", true),

    // Net Income
    NET_INCOME_BEFORE_TAX("Net Income Before Tax", "Pre-tax income", true),
    NET_INCOME("Net Income", "Bottom line profit", true);

    private final String displayName;
    private final String description;
    private final boolean isCredit; // true = credit increases, false = debit increases

    IncomeStatementLineItem(String displayName, String description, boolean isCredit) {
        this.displayName = displayName;
        this.description = description;
        this.isCredit = isCredit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCredit() {
        return isCredit;
    }

    public boolean isRevenue() {
        return this == REVENUE || this == SALES_REVENUE || this == SERVICE_REVENUE ||
               this == INTEREST_INCOME || this == COMMISSION_INCOME || this == OTHER_INCOME;
    }

    public boolean isExpense() {
        return name().endsWith("_EXPENSE") || this == COST_OF_REVENUE || this == COST_OF_GOODS_SOLD;
    }
}
