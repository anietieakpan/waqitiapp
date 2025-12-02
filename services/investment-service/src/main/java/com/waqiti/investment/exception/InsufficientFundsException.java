package com.waqiti.investment.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends InvestmentException {
    
    private final BigDecimal available;
    private final BigDecimal required;
    
    public InsufficientFundsException(BigDecimal available, BigDecimal required) {
        super(String.format("Insufficient funds. Available: $%.2f, Required: $%.2f", 
                           available, required));
        this.available = available;
        this.required = required;
    }
    
    public BigDecimal getAvailable() {
        return available;
    }
    
    public BigDecimal getRequired() {
        return required;
    }
}