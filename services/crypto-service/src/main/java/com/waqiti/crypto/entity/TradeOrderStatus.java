package com.waqiti.crypto.entity;

public enum TradeOrderStatus {
    PENDING,           // Order created but not yet submitted to exchange
    SUBMITTED,         // Order submitted to matching engine
    ACKNOWLEDGED,      // Order acknowledged by matching engine
    PARTIALLY_FILLED,  // Order partially executed
    FILLED,            // Order completely executed
    CANCELLED,         // Order cancelled by user or system
    REJECTED,          // Order rejected by system
    EXPIRED,           // Order expired (GTT orders)
    SUSPENDED,         // Order temporarily suspended
    TRIGGERED,         // Stop order triggered (waiting for execution)
    REPLACED,          // Order replaced by modification
    PENDING_CANCEL,    // Cancel request submitted
    PENDING_REPLACE;   // Replace request submitted
    
    public boolean isCancellable() {
        return this == PENDING || 
               this == SUBMITTED || 
               this == ACKNOWLEDGED || 
               this == PARTIALLY_FILLED ||
               this == SUSPENDED;
    }
    
    public boolean isModifiable() {
        return this == PENDING || 
               this == ACKNOWLEDGED;
    }
    
    public boolean isFinal() {
        return this == FILLED || 
               this == CANCELLED || 
               this == REJECTED || 
               this == EXPIRED ||
               this == REPLACED;
    }
    
    public boolean isActive() {
        return this == PENDING || 
               this == SUBMITTED || 
               this == ACKNOWLEDGED || 
               this == PARTIALLY_FILLED ||
               this == TRIGGERED;
    }
}