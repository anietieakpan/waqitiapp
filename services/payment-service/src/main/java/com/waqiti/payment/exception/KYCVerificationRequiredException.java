package com.waqiti.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PRECONDITION_REQUIRED)
public class KYCVerificationRequiredException extends RuntimeException {
    
    public KYCVerificationRequiredException(String message) {
        super(message);
    }
    
    public KYCVerificationRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}