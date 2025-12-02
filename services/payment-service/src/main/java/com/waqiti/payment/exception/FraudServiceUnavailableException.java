package com.waqiti.payment.exception;

/**
 * Exception thrown when fraud detection service is unavailable
 *
 * FAIL-CLOSED PATTERN:
 * - Payment is BLOCKED when fraud service is unavailable
 * - Prevents fraud during service outages
 * - Aligns with PCI DSS security requirements
 *
 * HTTP Status: 503 Service Unavailable
 * Retry: Client should retry after brief delay
 */
public class FraudServiceUnavailableException extends PaymentProcessingException {

    public FraudServiceUnavailableException(String message) {
        super(message);
    }

    public FraudServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
