package com.waqiti.investment.exception;

public class AccountNotFoundException extends InvestmentException {
    
    public AccountNotFoundException(String accountId) {
        super("Investment account not found: " + accountId);
    }
    
    public AccountNotFoundException(String message, String accountId) {
        super(message + ": " + accountId);
    }
}