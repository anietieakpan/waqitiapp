package com.waqiti.recurringpayment.exception;

public class RecurringPaymentNotFoundException extends RuntimeException {
    public RecurringPaymentNotFoundException(String message) {
        super(message);
    }
    
    public RecurringPaymentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
