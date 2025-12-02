package com.waqiti.common.exception;

/**
 * Exception thrown when a payment operation is invalid or not allowed
 */
public class InvalidPaymentOperationException extends BusinessException {
    
    public InvalidPaymentOperationException(String message) {
        super(message);
    }
    
    public InvalidPaymentOperationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public InvalidPaymentOperationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public InvalidPaymentOperationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}