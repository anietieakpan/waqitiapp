package com.waqiti.payment.exception;

/**
 * Exception thrown when fraud is detected in a transaction
 */
public class FraudDetectedException extends RuntimeException {
    
    private final String riskScore;
    private final String fraudIndicators;
    
    public FraudDetectedException(String message) {
        this(message, null, null);
    }
    
    public FraudDetectedException(String message, String riskScore, String fraudIndicators) {
        super(message);
        this.riskScore = riskScore;
        this.fraudIndicators = fraudIndicators;
    }
    
    public String getRiskScore() {
        return riskScore;
    }
    
    public String getFraudIndicators() {
        return fraudIndicators;
    }
}