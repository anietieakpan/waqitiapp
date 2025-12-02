package com.waqiti.crypto.exception;

public class PriceUnavailableException extends RuntimeException {

    public PriceUnavailableException(String message) {
        super(message);
    }

    public PriceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}