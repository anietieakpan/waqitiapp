package com.waqiti.expense.exception;

/**
 * Exception thrown when expense data is invalid
 */
public class InvalidExpenseException extends RuntimeException {

    public InvalidExpenseException(String message) {
        super(message);
    }

    public InvalidExpenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
