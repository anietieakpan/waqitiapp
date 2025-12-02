package com.waqiti.common.exception;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exception thrown when a wallet has insufficient funds for a transaction
 */
@Getter
public class InsufficientFundsException extends BusinessException {
    
    private final UUID walletId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableAmount;
    
    public InsufficientFundsException(UUID walletId, BigDecimal requestedAmount, BigDecimal availableAmount) {
        super(String.format("Insufficient funds in wallet %s. Requested: %s, Available: %s", 
            walletId, requestedAmount, availableAmount));
        this.walletId = walletId;
        this.requestedAmount = requestedAmount;
        this.availableAmount = availableAmount;
    }
    
    // Legacy constructors for backward compatibility
    public InsufficientFundsException(String message) {
        super(message);
        this.walletId = null;
        this.requestedAmount = null;
        this.availableAmount = null;
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
        this.walletId = null;
        this.requestedAmount = null;
        this.availableAmount = null;
    }

    public InsufficientFundsException(BigDecimal available, BigDecimal required, String currency) {
        super(String.format("Insufficient funds: available %s %s, required %s %s",
                available, currency, required, currency));
        this.walletId = null;
        this.requestedAmount = required;
        this.availableAmount = available;
    }
    
    // Methods expected by GlobalErrorHandler
    public BigDecimal getAvailableBalance() {
        return availableAmount;
    }
    
    public BigDecimal getRequiredAmount() {
        return requestedAmount;
    }
}