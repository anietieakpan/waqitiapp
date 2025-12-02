package com.waqiti.payment.exception;

/**
 * Base exception for check deposit operations
 */
public class CheckDepositException extends RuntimeException {
    
    public CheckDepositException(String message) {
        super(message);
    }
    
    public CheckDepositException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public CheckDepositException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}