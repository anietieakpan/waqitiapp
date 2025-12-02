package com.waqiti.account.exception;

public class AccountOperationNotAllowedException extends RuntimeException {
    public AccountOperationNotAllowedException(String message) {
        super(message);
    }
    
    public AccountOperationNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }
}