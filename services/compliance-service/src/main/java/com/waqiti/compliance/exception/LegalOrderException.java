package com.waqiti.compliance.exception;

/**
 * CRITICAL P0 FIX: Legal Order Exception
 *
 * Exception thrown when legal order processing fails.
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
public class LegalOrderException extends RuntimeException {

    public LegalOrderException(String message) {
        super(message);
    }

    public LegalOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
