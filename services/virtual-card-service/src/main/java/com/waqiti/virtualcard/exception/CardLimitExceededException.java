package com.waqiti.virtualcard.exception;

/**
 * Exception thrown when user exceeds card limit
 */
public class CardLimitExceededException extends RuntimeException {

    public CardLimitExceededException(String message) {
        super(message);
    }
}
