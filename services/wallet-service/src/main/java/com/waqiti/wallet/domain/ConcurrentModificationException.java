package com.waqiti.wallet.domain;

/**
 * Thrown when a wallet operation fails due to a concurrent modification
 */
public class ConcurrentModificationException extends RuntimeException {
    public ConcurrentModificationException(String message) {
        super(message);
    }
}
