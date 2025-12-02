package com.waqiti.crypto.exception;

/**
 * Signing Exception
 *
 * Thrown when blockchain transaction signing fails
 *
 * @author Waqiti Blockchain Team
 */
public class SigningException extends RuntimeException {

    public SigningException(String message) {
        super(message);
    }

    public SigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
