package com.waqiti.corebanking.exception;

import com.waqiti.common.exception.BusinessException;

/**
 * Exception thrown when unable to resolve user account for transaction
 */
public class AccountResolutionException extends BusinessException {
    
    private final String userId;
    private final String currency;
    
    public AccountResolutionException(String message, String userId, String currency) {
        super(message);
        this.userId = userId;
        this.currency = currency;
    }
    
    public AccountResolutionException(String message, String userId, String currency, Throwable cause) {
        super(message, cause);
        this.userId = userId;
        this.currency = currency;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getCurrency() {
        return currency;
    }
}