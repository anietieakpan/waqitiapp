package com.waqiti.compliance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// AML Exceptions
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class AMLScreeningException extends RuntimeException {
    public AMLScreeningException(String message) { super(message); }
    public AMLScreeningException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class AMLRetrievalException extends RuntimeException {
    public AMLRetrievalException(String message) { super(message); }
    public AMLRetrievalException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class AMLAlertResolutionException extends RuntimeException {
    public AMLAlertResolutionException(String message) { super(message); }
    public AMLAlertResolutionException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.NOT_FOUND)
class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(String message) { super(message); }
    public AlertNotFoundException(String message, Throwable cause) { super(message, cause); }
}

// SAR Exceptions
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class SARFilingException extends RuntimeException {
    public SARFilingException(String message) { super(message); }
    public SARFilingException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class ComplianceReportingException extends RuntimeException {
    public ComplianceReportingException(String message) { super(message); }
    public ComplianceReportingException(String message, Throwable cause) { super(message, cause); }
}

// Risk Assessment Exceptions
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class RiskAssessmentException extends RuntimeException {
    public RiskAssessmentException(String message) { super(message); }
    public RiskAssessmentException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class RiskAssessmentRetrievalException extends RuntimeException {
    public RiskAssessmentRetrievalException(String message) { super(message); }
    public RiskAssessmentRetrievalException(String message, Throwable cause) { super(message, cause); }
}

// Compliance Rule Exceptions
@ResponseStatus(HttpStatus.CONFLICT)
class ComplianceRuleException extends RuntimeException {
    public ComplianceRuleException(String message) { super(message); }
    public ComplianceRuleException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class ComplianceRuleCreationException extends RuntimeException {
    public ComplianceRuleCreationException(String message) { super(message); }
    public ComplianceRuleCreationException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class ComplianceRuleUpdateException extends RuntimeException {
    public ComplianceRuleUpdateException(String message) { super(message); }
    public ComplianceRuleUpdateException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.NOT_FOUND)
class ComplianceRuleNotFoundException extends RuntimeException {
    public ComplianceRuleNotFoundException(String message) { super(message); }
    public ComplianceRuleNotFoundException(String message, Throwable cause) { super(message, cause); }
}

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class ComplianceRuleRetrievalException extends RuntimeException {
    public ComplianceRuleRetrievalException(String message) { super(message); }
    public ComplianceRuleRetrievalException(String message, Throwable cause) { super(message, cause); }
}

// Monitoring Exceptions
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class ComplianceMonitoringException extends RuntimeException {
    public ComplianceMonitoringException(String message) { super(message); }
    public ComplianceMonitoringException(String message, Throwable cause) { super(message, cause); }
}
