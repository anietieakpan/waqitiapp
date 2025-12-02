package com.waqiti.common.compensation;

/**
 * Exception thrown when compensation operations fail.
 */
public class CompensationException extends RuntimeException {

    public CompensationException(String message) {
        super(message);
    }

    public CompensationException(String message, Throwable cause) {
        super(message, cause);
    }
}
