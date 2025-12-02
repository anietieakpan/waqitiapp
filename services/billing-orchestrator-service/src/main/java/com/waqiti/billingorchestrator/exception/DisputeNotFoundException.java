package com.waqiti.billingorchestrator.exception;

/**
 * Exception thrown when billing dispute is not found
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
public class DisputeNotFoundException extends RuntimeException {

    public DisputeNotFoundException(String message) {
        super(message);
    }

    public DisputeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
