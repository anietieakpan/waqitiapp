package com.waqiti.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for payment processing errors
 */
@Getter
public class PaymentProcessingException extends RuntimeException {
    
    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Object[] args;
    
    public PaymentProcessingException(String message) {
        super(message);
        this.errorCode = "PAYMENT_ERROR";
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.args = new Object[0];
    }
    
    public PaymentProcessingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.args = new Object[0];
    }
    
    public PaymentProcessingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = new Object[0];
    }
    
    public PaymentProcessingException(String message, String errorCode, HttpStatus httpStatus, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = args;
    }
    
    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "PAYMENT_ERROR";
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.args = new Object[0];
    }
    
    public PaymentProcessingException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.args = new Object[0];
    }
}

