package com.waqiti.common.validation;

/**
 * Severity levels for validation errors
 */
public enum ValidationSeverity {
    ERROR,      // Critical error - request should be rejected
    WARNING,    // Warning - request can proceed but user should be notified
    INFO        // Informational - minor issue or suggestion
}