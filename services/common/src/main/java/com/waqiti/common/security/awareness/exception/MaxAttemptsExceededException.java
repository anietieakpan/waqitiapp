package com.waqiti.common.security.awareness.exception;

/**
 * Exception thrown when maximum attempts exceeded
 *
 * @author Waqiti Platform Team
 */
public class MaxAttemptsExceededException extends SecurityAwarenessException {

    public MaxAttemptsExceededException(String message) {
        super(message);
    }

    public MaxAttemptsExceededException(int maxAttempts) {
        super("Maximum attempts exceeded: " + maxAttempts);
    }
}