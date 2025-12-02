package com.waqiti.common.exception;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Exception thrown when a payment exceeds user limits
 */
@Getter
public class PaymentLimitExceededException extends RuntimeException {
    
    private final String limitType;
    private final BigDecimal requestedAmount;
    private final BigDecimal limitAmount;
    
    public PaymentLimitExceededException(String limitType, BigDecimal requestedAmount, BigDecimal limitAmount) {
        super(String.format("Payment amount %s exceeds %s limit of %s", 
            requestedAmount, limitType, limitAmount));
        this.limitType = limitType;
        this.requestedAmount = requestedAmount;
        this.limitAmount = limitAmount;
    }
}