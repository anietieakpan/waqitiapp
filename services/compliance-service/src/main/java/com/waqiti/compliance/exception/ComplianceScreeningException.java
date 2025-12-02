package com.waqiti.compliance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ComplianceScreeningException extends RuntimeException {
    
    public ComplianceScreeningException(String message) {
        super(message);
    }
    
    public ComplianceScreeningException(String message, Throwable cause) {
        super(message, cause);
    }
}