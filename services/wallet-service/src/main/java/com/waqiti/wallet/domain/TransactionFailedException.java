package com.waqiti.wallet.domain;

/**
 * Thrown when a transaction fails
 */
public class TransactionFailedException extends RuntimeException {
    public TransactionFailedException(String message) {
        super(message);
    }

    public TransactionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
