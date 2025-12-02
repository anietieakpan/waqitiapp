package com.waqiti.recurringpayment.exception;

public class RecurringExecutionException extends RuntimeException {
    public RecurringExecutionException(String message) {
        super(message);
    }
    
    public RecurringExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
