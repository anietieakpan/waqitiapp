package com.waqiti.crypto.exception;

/**
 * Exception thrown when a requested operation is not supported for a specific cryptocurrency
 */
public class UnsupportedCryptoCurrencyException extends CryptoServiceException {
    
    public UnsupportedCryptoCurrencyException(String message) {
        super(message);
    }
    
    public UnsupportedCryptoCurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}