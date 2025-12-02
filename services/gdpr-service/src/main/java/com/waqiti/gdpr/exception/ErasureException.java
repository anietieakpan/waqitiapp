package com.waqiti.gdpr.exception;

public class ErasureException extends RuntimeException {
    public ErasureException(String message) {
        super(message);
    }

    public ErasureException(String message, Throwable cause) {
        super(message, cause);
    }
}