package com.waqiti.common.metrics.abstraction;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit Breaker for Metrics
 * Prevents metrics system from overwhelming the application
 */
@Slf4j
public class MetricsCircuitBreaker {
    
    private final MetricsConfiguration configuration;
    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicLong lastFailureTime;
    private final AtomicLong lastStateChangeTime;
    private final AtomicLong totalRequests;
    private final AtomicLong rejectedRequests;
    
    public enum State {
        CLOSED,      // Normal operation
        OPEN,        // Circuit broken, rejecting requests
        HALF_OPEN    // Testing if system recovered
    }
    
    public MetricsCircuitBreaker(MetricsConfiguration configuration) {
        this.configuration = configuration;
        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
        this.lastStateChangeTime = new AtomicLong(System.currentTimeMillis());
        this.totalRequests = new AtomicLong(0);
        this.rejectedRequests = new AtomicLong(0);
    }
    
    /**
     * Check if request is allowed
     */
    public boolean allowRequest() {
        if (!configuration.isEnableCircuitBreaker()) {
            return true;
        }
        
        totalRequests.incrementAndGet();
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                if (shouldAttemptReset()) {
                    transitionTo(State.HALF_OPEN);
                    return true;
                }
                rejectedRequests.incrementAndGet();
                return false;
                
            case HALF_OPEN:
                // Allow limited requests in half-open state
                return totalRequests.get() % 10 == 0; // Allow 10% of requests
                
            default:
                return true;
        }
    }
    
    /**
     * Record successful request
     */
    public void recordSuccess() {
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            // Success in half-open state, consider closing
            int failures = failureCount.get();
            if (failures < configuration.getCircuitBreakerThreshold() / 2) {
                transitionTo(State.CLOSED);
                failureCount.set(0);
            }
        } else if (currentState == State.CLOSED) {
            // Decay failure count on success
            failureCount.updateAndGet(count -> Math.max(0, count - 1));
        }
    }
    
    /**
     * Record failed request
     */
    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        int failures = failureCount.incrementAndGet();
        
        State currentState = state.get();
        
        if (currentState == State.CLOSED) {
            if (failures >= configuration.getCircuitBreakerThreshold()) {
                transitionTo(State.OPEN);
                log.warn("Metrics circuit breaker opened after {} failures", failures);
            }
        } else if (currentState == State.HALF_OPEN) {
            // Failure in half-open state, reopen immediately
            transitionTo(State.OPEN);
            log.warn("Metrics circuit breaker reopened after failure in half-open state");
        }
    }
    
    /**
     * Check if we should attempt to reset the circuit
     */
    private boolean shouldAttemptReset() {
        long timeSinceLastStateChange = System.currentTimeMillis() - lastStateChangeTime.get();
        return timeSinceLastStateChange >= configuration.getCircuitBreakerTimeout().toMillis();
    }
    
    /**
     * Transition to new state
     */
    private void transitionTo(State newState) {
        State oldState = state.getAndSet(newState);
        lastStateChangeTime.set(System.currentTimeMillis());
        
        if (oldState != newState) {
            log.info("Metrics circuit breaker state changed: {} -> {}", oldState, newState);
            
            if (newState == State.CLOSED) {
                // Reset counters when closing
                failureCount.set(0);
                rejectedRequests.set(0);
            }
        }
    }
    
    /**
     * Force reset the circuit breaker
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        lastFailureTime.set(0);
        lastStateChangeTime.set(System.currentTimeMillis());
        rejectedRequests.set(0);
        log.info("Metrics circuit breaker manually reset");
    }
    
    /**
     * Get current state
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Get circuit breaker statistics
     */
    public CircuitBreakerStats getStats() {
        return CircuitBreakerStats.builder()
            .state(state.get())
            .failureCount(failureCount.get())
            .totalRequests(totalRequests.get())
            .rejectedRequests(rejectedRequests.get())
            .lastFailureTime(lastFailureTime.get())
            .lastStateChangeTime(lastStateChangeTime.get())
            .build();
    }
    
    /**
     * Circuit breaker statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class CircuitBreakerStats {
        private final State state;
        private final int failureCount;
        private final long totalRequests;
        private final long rejectedRequests;
        private final long lastFailureTime;
        private final long lastStateChangeTime;
    }
}