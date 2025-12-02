package com.waqiti.wallet.domain;




/**
 * Thrown when an operation is attempted on a wallet that is not active
 */
public class WalletNotActiveException extends RuntimeException {
    public WalletNotActiveException(String message) {
        super(message);
    }
}

