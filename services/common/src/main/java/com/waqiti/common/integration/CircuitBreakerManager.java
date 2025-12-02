package com.waqiti.common.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Circuit breaker manager for fault tolerance
 */
@Slf4j
@Component
public class CircuitBreakerManager {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Map<String, CircuitBreakerConfig> customConfigs = new ConcurrentHashMap<>();
    
    public CircuitBreakerManager() {
        // Default circuit breaker configuration
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // 50% failure rate threshold
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s in open state
            .slowCallRateThreshold(50) // 50% slow call rate threshold
            .slowCallDurationThreshold(Duration.ofSeconds(2)) // Calls slower than 2s are slow
            .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open state
            .minimumNumberOfCalls(10) // Minimum 10 calls before calculating failure rate
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(100) // Sliding window of 100 calls
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
            
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(defaultConfig);
    }
    
    /**
     * Get or create circuit breaker for service
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakerRegistry.circuitBreaker(serviceName);
    }
    
    /**
     * Get circuit breaker with custom configuration
     */
    public CircuitBreaker getCircuitBreaker(String serviceName, CircuitBreakerConfig config) {
        customConfigs.put(serviceName, config);
        return circuitBreakerRegistry.circuitBreaker(serviceName, config);
    }
    
    /**
     * Execute supplier with circuit breaker protection
     */
    public <T> T executeWithCircuitBreaker(String serviceName, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        return circuitBreaker.executeSupplier(supplier);
    }
    
    /**
     * Execute supplier with circuit breaker and fallback
     */
    public <T> T executeWithFallback(String serviceName, Supplier<T> supplier, Supplier<T> fallback) {
        try {
            CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
            return circuitBreaker.executeSupplier(supplier);
        } catch (Exception e) {
            log.warn("Circuit breaker {} triggered, using fallback", serviceName, e);
            return fallback.get();
        }
    }
    
    /**
     * Check if circuit breaker is open
     */
    public boolean isOpen(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }
    
    /**
     * Check if circuit breaker is closed
     */
    public boolean isClosed(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        return circuitBreaker.getState() == CircuitBreaker.State.CLOSED;
    }
    
    /**
     * Check if circuit breaker is half-open
     */
    public boolean isHalfOpen(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        return circuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN;
    }
    
    /**
     * Get circuit breaker state
     */
    public String getState(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        return circuitBreaker.getState().name();
    }
    
    /**
     * Get circuit breaker metrics
     */
    public CircuitBreakerMetrics getMetrics(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        
        return CircuitBreakerMetrics.builder()
            .serviceName(serviceName)
            .state(circuitBreaker.getState().name())
            .failureRate(metrics.getFailureRate())
            .slowCallRate(metrics.getSlowCallRate())
            .numberOfBufferedCalls(metrics.getNumberOfBufferedCalls())
            .numberOfFailedCalls(metrics.getNumberOfFailedCalls())
            .numberOfSuccessfulCalls(metrics.getNumberOfSuccessfulCalls())
            .numberOfSlowCalls(metrics.getNumberOfSlowCalls())
            .numberOfNotPermittedCalls(metrics.getNumberOfNotPermittedCalls())
            .build();
    }
    
    /**
     * Check if circuit breaker can execute
     */
    public boolean canExecute(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }

    /**
     * Record successful call
     */
    public void recordSuccess(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        circuitBreaker.onSuccess(System.currentTimeMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Record failed call
     */
    public void recordFailure(String serviceName, Throwable throwable) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        circuitBreaker.onError(System.currentTimeMillis(), java.util.concurrent.TimeUnit.MILLISECONDS, throwable);
    }

    /**
     * Reset circuit breaker
     */
    public void reset(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        circuitBreaker.reset();
        log.info("Reset circuit breaker for service: {}", serviceName);
    }
    
    /**
     * Force open circuit breaker
     */
    public void forceOpen(String serviceName) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        circuitBreaker.transitionToOpenState();
        log.warn("Forced circuit breaker to open state for service: {}", serviceName);
    }
    
    /**
     * Create custom configuration
     */
    public CircuitBreakerConfig createCustomConfig(
            int failureRateThreshold,
            int slowCallRateThreshold,
            Duration waitDurationInOpenState,
            Duration slowCallDurationThreshold,
            int minimumNumberOfCalls) {
        
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(failureRateThreshold)
            .slowCallRateThreshold(slowCallRateThreshold)
            .waitDurationInOpenState(waitDurationInOpenState)
            .slowCallDurationThreshold(slowCallDurationThreshold)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(100)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    }
    
    /**
     * Get all circuit breaker states
     */
    public Map<String, String> getAllStates() {
        Map<String, String> states = new ConcurrentHashMap<>();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            states.put(cb.getName(), cb.getState().name());
        });
        return states;
    }

    /**
     * Shutdown circuit breaker manager
     */
    public void shutdown() {
        log.info("Shutting down Circuit Breaker Manager");
        // Reset all circuit breakers
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            try {
                cb.reset();
            } catch (Exception e) {
                log.warn("Error resetting circuit breaker: {}", cb.getName(), e);
            }
        });
    }

    /**
     * Circuit breaker metrics DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class CircuitBreakerMetrics {
        private String serviceName;
        private String state;
        private float failureRate;
        private float slowCallRate;
        private int numberOfBufferedCalls;
        private int numberOfFailedCalls;
        private int numberOfSuccessfulCalls;
        private int numberOfSlowCalls;
        private long numberOfNotPermittedCalls;
    }
}