package com.waqiti.reporting.exception;

import com.waqiti.common.exception.BusinessException;

public class ReportGenerationException extends BusinessException {
    
    public ReportGenerationException(String message) {
        super(message);
    }
    
    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}