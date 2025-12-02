package com.waqiti.investment.exception;

public class AccountAlreadyExistsException extends InvestmentException {
    
    public AccountAlreadyExistsException(String customerId) {
        super("Investment account already exists for customer: " + customerId);
    }
}