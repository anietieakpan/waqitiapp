package com.waqiti.frauddetection.sanctions.exception;

/**
 * Exception thrown when sanctions screening fails.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
public class SanctionsScreeningException extends RuntimeException {

    public SanctionsScreeningException(String message) {
        super(message);
    }

    public SanctionsScreeningException(String message, Throwable cause) {
        super(message, cause);
    }
}
