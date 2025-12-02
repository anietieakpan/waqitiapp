package com.waqiti.virtualcard.exception;

/**
 * Exception thrown when a requested card is not found
 */
public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(String message) {
        super(message);
    }

    public CardNotFoundException(String cardId, String userId) {
        super(String.format("Card not found: cardId=%s, userId=%s", cardId, userId));
    }
}
