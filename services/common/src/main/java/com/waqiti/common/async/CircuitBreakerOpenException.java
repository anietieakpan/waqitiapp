package com.waqiti.common.async;

/**
 * Exception thrown when circuit breaker is open
 */
public class CircuitBreakerOpenException extends AsyncOperationException {
    
    public CircuitBreakerOpenException(String message) {
        super(message);
    }
    
    public CircuitBreakerOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}