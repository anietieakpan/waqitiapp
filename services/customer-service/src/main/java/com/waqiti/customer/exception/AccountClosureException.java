package com.waqiti.customer.exception;

/**
 * Exception thrown when account closure operations fail
 */
public class AccountClosureException extends RuntimeException {

    private final String accountId;
    private final String errorCode;

    public AccountClosureException(String message, String accountId) {
        super(message);
        this.accountId = accountId;
        this.errorCode = "ACCOUNT_CLOSURE_ERROR";
    }

    public AccountClosureException(String message, String accountId, String errorCode) {
        super(message);
        this.accountId = accountId;
        this.errorCode = errorCode;
    }

    public AccountClosureException(String message, String accountId, Throwable cause) {
        super(message, cause);
        this.accountId = accountId;
        this.errorCode = "ACCOUNT_CLOSURE_ERROR";
    }

    public String getAccountId() {
        return accountId;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
