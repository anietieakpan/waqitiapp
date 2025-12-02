package com.waqiti.corebanking.exception;

import java.util.UUID;

/**
 * Exception thrown when statement job creation fails
 */
public class StatementJobCreationException extends CoreBankingException {
    
    private final UUID accountId;
    private final UUID userId;
    
    public StatementJobCreationException(String message, UUID accountId, UUID userId) {
        super(message);
        this.accountId = accountId;
        this.userId = userId;
    }
    
    public StatementJobCreationException(String message, UUID accountId, UUID userId, Throwable cause) {
        super(message, cause);
        this.accountId = accountId;
        this.userId = userId;
    }
    
    public UUID getAccountId() {
        return accountId;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    @Override
    public String getErrorCode() {
        return "STATEMENT_JOB_CREATION_FAILED";
    }
}