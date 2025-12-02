package com.waqiti.grouppayment.exception;

public class InsufficientPermissionException extends RuntimeException {
    public InsufficientPermissionException(String message) {
        super(message);
    }
}