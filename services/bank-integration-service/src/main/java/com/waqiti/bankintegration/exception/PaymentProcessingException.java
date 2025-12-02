package com.waqiti.bankintegration.exception;

import com.waqiti.common.exception.BusinessException;

/**
 * Exception thrown when payment processing fails
 */
public class PaymentProcessingException extends BusinessException {
    
    public PaymentProcessingException(String message) {
        super(message);
    }
    
    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public PaymentProcessingException(String message, String errorCode) {
        super(message, errorCode);
    }
    
    public PaymentProcessingException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}