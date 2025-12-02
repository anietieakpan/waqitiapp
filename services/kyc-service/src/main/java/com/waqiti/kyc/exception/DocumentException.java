package com.waqiti.kyc.exception;

/**
 * Exception thrown when document operations fail
 */
public class DocumentException extends RuntimeException {
    
    public DocumentException(String message) {
        super(message);
    }
    
    public DocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}