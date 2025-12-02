/**
 * Scheduled Payment Exception
 * Custom exception for scheduled payment operations
 */
package com.waqiti.payment.exception;

public class ScheduledPaymentException extends RuntimeException {
    
    public ScheduledPaymentException(String message) {
        super(message);
    }
    
    public ScheduledPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}