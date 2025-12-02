package com.waqiti.payment.cash.provider;

public class CircuitBreakerState {
    private CircuitState state = CircuitState.CLOSED;
    private int failureCount = 0;
    private long lastFailureTime = 0;
    private final long recoveryTimeMs = 60000; // 1 minute
    
    // Getters and setters
    public CircuitState getState() { return state; }
    public void setState(CircuitState state) { this.state = state; }
    public int getFailureCount() { return failureCount; }
    public long getLastFailureTime() { return lastFailureTime; }
    public void setLastFailureTime(long time) { this.lastFailureTime = time; }
    public long getRecoveryTimeMs() { return recoveryTimeMs; }
    
    public void recordFailure() { 
        this.failureCount++; 
        this.lastFailureTime = System.currentTimeMillis();
    }
    
    public void recordSuccess() { 
        this.failureCount = 0; 
        if (this.state == CircuitState.HALF_OPEN) {
            this.state = CircuitState.CLOSED;
        }
    }
}
