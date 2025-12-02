package com.waqiti.payment.exception;

import java.math.BigDecimal;
import java.util.List;

/**
 * Exception thrown when fraud is detected or fraud detection fails
 */
public class FraudDetectionException extends PaymentProcessingException {
    
    private final String customerId;
    private final BigDecimal riskScore;
    private final List<String> triggers;
    private final boolean blocked;
    
    public FraudDetectionException(String customerId, String message) {
        super("FRAUD_DETECTED", message);
        this.customerId = customerId;
        this.riskScore = null;
        this.triggers = null;
        this.blocked = true;
    }
    
    public FraudDetectionException(String customerId, BigDecimal riskScore, List<String> triggers, String message) {
        super("FRAUD_DETECTED", message);
        this.customerId = customerId;
        this.riskScore = riskScore;
        this.triggers = triggers;
        this.blocked = true;
    }
    
    public FraudDetectionException(String customerId, BigDecimal riskScore, List<String> triggers, 
                                 String message, String transactionId) {
        super("FRAUD_DETECTED", message, transactionId);
        this.customerId = customerId;
        this.riskScore = riskScore;
        this.triggers = triggers;
        this.blocked = true;
    }
    
    public FraudDetectionException(String customerId, BigDecimal riskScore, List<String> triggers, 
                                 boolean blocked, String message, String transactionId) {
        super(blocked ? "FRAUD_BLOCKED" : "FRAUD_RISK_HIGH", message, transactionId);
        this.customerId = customerId;
        this.riskScore = riskScore;
        this.triggers = triggers;
        this.blocked = blocked;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public BigDecimal getRiskScore() {
        return riskScore;
    }
    
    public List<String> getTriggers() {
        return triggers;
    }
    
    public boolean isBlocked() {
        return blocked;
    }
}