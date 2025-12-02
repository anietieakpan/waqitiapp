package com.waqiti.recurringpayment.exception;

public class InvalidRecurringStateException extends RuntimeException {
    public InvalidRecurringStateException(String message) {
        super(message);
    }
    
    public InvalidRecurringStateException(String message, Throwable cause) {
        super(message, cause);
    }
}