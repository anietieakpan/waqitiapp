package com.waqiti.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PRECONDITION_REQUIRED)
public class InsufficientKYCLevelException extends RuntimeException {
    
    public InsufficientKYCLevelException(String message) {
        super(message);
    }
    
    public InsufficientKYCLevelException(String message, Throwable cause) {
        super(message, cause);
    }
}