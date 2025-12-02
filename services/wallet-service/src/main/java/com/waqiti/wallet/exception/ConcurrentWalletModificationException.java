package com.waqiti.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a wallet operation fails due to concurrent modifications
 * This typically occurs during race conditions in high-concurrency scenarios
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConcurrentWalletModificationException extends RuntimeException {
    
    public ConcurrentWalletModificationException(String message) {
        super(message);
    }
    
    public ConcurrentWalletModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}