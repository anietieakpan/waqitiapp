package com.waqiti.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when payment limit is exceeded
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class PaymentLimitExceededException extends RuntimeException {
    
    private final Double requestedAmount;
    private final Double limitAmount;
    private final String limitType;
    
    public PaymentLimitExceededException(String message, Double requestedAmount, Double limitAmount, String limitType) {
        super(message);
        this.requestedAmount = requestedAmount;
        this.limitAmount = limitAmount;
        this.limitType = limitType;
    }
    
    public PaymentLimitExceededException(String message) {
        super(message);
        this.requestedAmount = null;
        this.limitAmount = null;
        this.limitType = null;
    }
    
    public Double getRequestedAmount() {
        return requestedAmount;
    }
    
    public Double getLimitAmount() {
        return limitAmount;
    }
    
    public String getLimitType() {
        return limitType;
    }
}