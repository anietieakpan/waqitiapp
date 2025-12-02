package com.waqiti.recurringpayment.exception;

public class RecurringLimitExceededException extends RuntimeException {
    public RecurringLimitExceededException(String message) {
        super(message);
    }
    
    public RecurringLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
