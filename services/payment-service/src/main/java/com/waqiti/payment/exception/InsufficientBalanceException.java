package com.waqiti.payment.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when a user has insufficient balance for a transaction
 */
public class InsufficientBalanceException extends PaymentProcessingException {
    
    private final String customerId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;
    private final String currency;
    
    public InsufficientBalanceException(String customerId, BigDecimal requestedAmount, 
                                      BigDecimal availableBalance, String currency) {
        super("INSUFFICIENT_BALANCE", 
              String.format("Insufficient balance for customer %s. Requested: %s %s, Available: %s %s",
                          customerId, requestedAmount, currency, availableBalance, currency));
        this.customerId = customerId;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
        this.currency = currency;
    }
    
    public InsufficientBalanceException(String customerId, BigDecimal requestedAmount, 
                                      BigDecimal availableBalance, String currency, String transactionId) {
        super("INSUFFICIENT_BALANCE", 
              String.format("Insufficient balance for customer %s. Requested: %s %s, Available: %s %s",
                          customerId, requestedAmount, currency, availableBalance, currency), 
              transactionId);
        this.customerId = customerId;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
        this.currency = currency;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
    
    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
    
    public String getCurrency() {
        return currency;
    }
}