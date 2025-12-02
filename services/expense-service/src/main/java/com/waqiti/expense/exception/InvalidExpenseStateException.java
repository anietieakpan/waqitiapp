package com.waqiti.expense.exception;

public class InvalidExpenseStateException extends RuntimeException {
    public InvalidExpenseStateException(String message) {
        super(message);
    }
}
