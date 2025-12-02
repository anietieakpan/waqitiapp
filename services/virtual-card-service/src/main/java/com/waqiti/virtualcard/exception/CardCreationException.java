package com.waqiti.virtualcard.exception;

/**
 * Exception thrown when card creation fails
 */
public class CardCreationException extends RuntimeException {

    public CardCreationException(String message) {
        super(message);
    }

    public CardCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
