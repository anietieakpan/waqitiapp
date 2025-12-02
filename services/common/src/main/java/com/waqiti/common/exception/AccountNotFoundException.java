package com.waqiti.common.exception;

/**
 * Exception thrown when a requested account cannot be found.
 * This is a business exception that should not trigger circuit breaker.
 */
public class AccountNotFoundException extends ResourceNotFoundException {

    private final String accountId;
    private final String userId;

    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
        this.accountId = accountId;
        this.userId = null;
    }

    public AccountNotFoundException(String message, String accountId) {
        super(message);
        this.accountId = accountId;
        this.userId = null;
    }

    public AccountNotFoundException(String message, String accountId, String userId) {
        super(message);
        this.accountId = accountId;
        this.userId = userId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getUserId() {
        return userId;
    }
}
