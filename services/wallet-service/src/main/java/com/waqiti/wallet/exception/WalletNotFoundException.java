package com.waqiti.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested wallet cannot be found
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class WalletNotFoundException extends RuntimeException {
    
    public WalletNotFoundException(String message) {
        super(message);
    }
    
    public WalletNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public WalletNotFoundException(String walletId) {
        super("Wallet not found with ID: " + walletId);
    }
}