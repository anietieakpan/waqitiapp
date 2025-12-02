package com.waqiti.card.exception;

/**
 * Base exception for card service
 */
public class CardServiceException extends RuntimeException {
    public CardServiceException(String message) {
        super(message);
    }

    public CardServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
