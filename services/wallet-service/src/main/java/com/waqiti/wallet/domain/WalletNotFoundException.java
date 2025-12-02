package com.waqiti.wallet.domain;

/**
 * Thrown when a wallet is not found
 */
public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(String message) {
        super(message);
    }
}