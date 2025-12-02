package com.waqiti.reporting.exception;

import com.waqiti.common.exception.ResourceNotFoundException;

public class ReportNotFoundException extends ResourceNotFoundException {
    
    public ReportNotFoundException(String message) {
        super(message);
    }
    
    public ReportNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ReportNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(resourceName, fieldName, fieldValue);
    }
}