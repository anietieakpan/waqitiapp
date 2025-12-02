package com.waqiti.auth.exception;

public class MFAException extends RuntimeException {
    public MFAException(String message) {
        super(message);
    }

    public MFAException(String message, Throwable cause) {
        super(message, cause);
    }
}
