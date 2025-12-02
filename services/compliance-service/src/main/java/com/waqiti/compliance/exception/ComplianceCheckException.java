package com.waqiti.compliance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ComplianceCheckException extends RuntimeException {
    
    public ComplianceCheckException(String message) {
        super(message);
    }
    
    public ComplianceCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}