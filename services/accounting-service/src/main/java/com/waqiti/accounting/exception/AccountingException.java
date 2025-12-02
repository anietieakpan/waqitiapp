package com.waqiti.accounting.exception;

/**
 * Base exception for all accounting-related errors
 */
public class AccountingException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;

    public AccountingException(String message) {
        super(message);
        this.errorCode = "ACCOUNTING_ERROR";
        this.args = null;
    }

    public AccountingException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    public AccountingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "ACCOUNTING_ERROR";
        this.args = null;
    }

    public AccountingException(String errorCode, String message, Throwable cause, Object... args) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }
}
