package com.waqiti.payment.exception;

/**
 * Exception thrown when ACH transfer operations fail
 */
public class ACHTransferException extends RuntimeException {
    
    public ACHTransferException(String message) {
        super(message);
    }
    
    public ACHTransferException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ACHTransferException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}