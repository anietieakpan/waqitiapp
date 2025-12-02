package com.waqiti.virtualcard.exception;

/**
 * Exception thrown when card secrets (PAN, CVV) cannot be retrieved
 */
public class CardSecretsRetrievalException extends RuntimeException {

    public CardSecretsRetrievalException(String message) {
        super(message);
    }

    public CardSecretsRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
