package com.waqiti.expense.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when budget is exceeded
 */
public class BudgetExceededException extends RuntimeException {

    private final String category;
    private final BigDecimal amount;
    private final BigDecimal budgetLimit;

    public BudgetExceededException(String category, BigDecimal amount, BigDecimal budgetLimit) {
        super(String.format("Budget exceeded for category '%s'. Expense amount: %s, Budget limit: %s, Overage: %s",
            category, amount, budgetLimit, amount.subtract(budgetLimit)));
        this.category = category;
        this.amount = amount;
        this.budgetLimit = budgetLimit;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBudgetLimit() {
        return budgetLimit;
    }

    public BigDecimal getOverage() {
        return amount.subtract(budgetLimit);
    }
}
