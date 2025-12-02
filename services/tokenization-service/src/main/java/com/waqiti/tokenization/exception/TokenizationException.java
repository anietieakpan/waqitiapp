package com.waqiti.tokenization.exception;

/**
 * Tokenization Exception
 *
 * Thrown when card tokenization fails
 *
 * @author Waqiti Security Team
 */
public class TokenizationException extends RuntimeException {

    public TokenizationException(String message) {
        super(message);
    }

    public TokenizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
