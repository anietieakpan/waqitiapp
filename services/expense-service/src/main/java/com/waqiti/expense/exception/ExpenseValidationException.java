package com.waqiti.expense.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown when expense validation fails
 */
public class ExpenseValidationException extends RuntimeException {

    private final List<String> validationErrors;

    public ExpenseValidationException(String message) {
        super(message);
        this.validationErrors = new ArrayList<>();
        this.validationErrors.add(message);
    }

    public ExpenseValidationException(List<String> validationErrors) {
        super("Expense validation failed: " + String.join(", ", validationErrors));
        this.validationErrors = validationErrors;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
