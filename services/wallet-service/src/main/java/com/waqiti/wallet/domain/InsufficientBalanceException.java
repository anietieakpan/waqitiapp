package com.waqiti.wallet.domain;


/**
 * Thrown when a wallet has insufficient balance for a transaction
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}




