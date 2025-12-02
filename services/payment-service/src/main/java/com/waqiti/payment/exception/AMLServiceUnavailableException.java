package com.waqiti.payment.exception;

/**
 * Exception thrown when AML screening service is unavailable
 *
 * FAIL-CLOSED PATTERN:
 * - Payment is BLOCKED when AML service is unavailable
 * - Prevents OFAC sanctions violations during service outages
 * - Required for PATRIOT Act Section 326 compliance
 *
 * HTTP Status: 503 Service Unavailable
 * Retry: Client should retry after brief delay
 */
public class AMLServiceUnavailableException extends PaymentProcessingException {

    public AMLServiceUnavailableException(String message) {
        super(message);
    }

    public AMLServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
