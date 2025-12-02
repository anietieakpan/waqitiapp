package com.waqiti.tokenization.exception;

/**
 * Token Not Found Exception
 *
 * Thrown when a token cannot be found or user is unauthorized
 *
 * @author Waqiti Security Team
 */
public class TokenNotFoundException extends RuntimeException {

    public TokenNotFoundException(String message) {
        super(message);
    }

    public TokenNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
