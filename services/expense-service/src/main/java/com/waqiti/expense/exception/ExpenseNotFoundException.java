package com.waqiti.expense.exception;

import java.util.UUID;

/**
 * Exception thrown when expense is not found
 */
public class ExpenseNotFoundException extends RuntimeException {

    public ExpenseNotFoundException(UUID expenseId) {
        super("Expense not found with ID: " + expenseId);
    }

    public ExpenseNotFoundException(String message) {
        super(message);
    }
}
