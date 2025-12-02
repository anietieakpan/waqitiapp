package com.waqiti.payment.exception;

/**
 * Exception thrown when compliance service is unavailable
 *
 * FAIL-CLOSED PATTERN:
 * - Payment is BLOCKED when compliance service is unavailable
 * - Prevents AML/sanctions violations during service outages
 * - Required for BSA/AML regulatory compliance
 *
 * HTTP Status: 503 Service Unavailable
 * Retry: Client should retry after brief delay
 */
public class ComplianceServiceUnavailableException extends PaymentProcessingException {

    public ComplianceServiceUnavailableException(String message) {
        super(message);
    }

    public ComplianceServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
