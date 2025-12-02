package com.waqiti.recurringpayment.service;

// Fraud Check Result DTO
public class FraudCheckResult {
    private boolean blocked;
    private String reason;
    
    public FraudCheckResult(boolean blocked, String reason) {
        this.blocked = blocked;
        this.reason = reason;
    }
    
    public boolean isBlocked() { return blocked; }
    public String getReason() { return reason; }
}
