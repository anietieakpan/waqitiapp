package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Circuit Breaker Metrics
 * 
 * Detailed metrics for circuit breaker state and performance,
 * including failure rates, call counts, and state transitions.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerMetrics {
    
    /**
     * Current circuit breaker state (CLOSED, OPEN, HALF_OPEN)
     */
    private String state;
    
    /**
     * Failure rate percentage (0-100)
     */
    private Float failureRate;
    
    /**
     * Success rate percentage (0-100)
     */
    private Float successRate;
    
    /**
     * Total number of calls in the current window
     */
    private Integer numberOfCalls;
    
    /**
     * Number of failed calls in the current window
     */
    private Integer numberOfFailedCalls;
    
    /**
     * Number of successful calls in the current window
     */
    private Integer numberOfSuccessfulCalls;
    
    /**
     * Number of not permitted calls (when circuit is open)
     */
    private Integer numberOfNotPermittedCalls;
    
    /**
     * When the circuit breaker last opened
     */
    private LocalDateTime lastOpenedAt;
    
    /**
     * When the circuit breaker last closed
     */
    private LocalDateTime lastClosedAt;
    
    /**
     * Duration the circuit has been in current state (milliseconds)
     */
    private Long currentStateDurationMs;
    
    /**
     * Number of state transitions in the current period
     */
    private Integer stateTransitionCount;
    
    /**
     * Configured failure rate threshold
     */
    private Float failureRateThreshold;
    
    /**
     * Configured minimum number of calls
     */
    private Integer minimumNumberOfCalls;
    
    /**
     * Configured wait duration in open state (milliseconds)
     */
    private Long waitDurationInOpenStateMs;
    
    /**
     * Number of permitted calls in half-open state
     */
    private Integer permittedNumberOfCallsInHalfOpenState;
    
    /**
     * Sliding window size
     */
    private Integer slidingWindowSize;
    
    /**
     * Sliding window type (COUNT_BASED or TIME_BASED)
     */
    private String slidingWindowType;
    
    /**
     * Maximum wait duration for automatic transition from open to half-open
     */
    private Long maxWaitDurationInHalfOpenStateMs;
    
    /**
     * Check if circuit breaker is allowing calls
     */
    public boolean isAllowingCalls() {
        return !"OPEN".equals(state);
    }
    
    /**
     * Check if circuit breaker is healthy
     */
    public boolean isHealthy() {
        return "CLOSED".equals(state) && 
               (failureRate == null || failureRate < 10.0f);
    }
    
    /**
     * Check if circuit breaker has sufficient calls for decision making
     */
    public boolean hasSufficientCalls() {
        return numberOfCalls != null && 
               minimumNumberOfCalls != null &&
               numberOfCalls >= minimumNumberOfCalls;
    }
    
    /**
     * Check if failure rate exceeds threshold
     */
    public boolean isFailureRateExceeded() {
        return failureRate != null && 
               failureRateThreshold != null &&
               failureRate >= failureRateThreshold;
    }
    
    /**
     * Get the time until circuit breaker can transition to half-open
     */
    public long getTimeUntilHalfOpen() {
        if (!"OPEN".equals(state) || lastOpenedAt == null || waitDurationInOpenStateMs == null) {
            return 0;
        }
        
        long timeSinceOpened = java.time.Duration.between(lastOpenedAt, LocalDateTime.now()).toMillis();
        return Math.max(0, waitDurationInOpenStateMs - timeSinceOpened);
    }
    
    /**
     * Calculate effectiveness score of the circuit breaker
     */
    public int getEffectivenessScore() {
        if (numberOfCalls == null || numberOfCalls == 0) {
            return 100; // No calls, perfect score
        }
        
        // Score based on success rate and state stability
        int score = 100;
        
        if (failureRate != null) {
            score -= Math.min(50, (int)(failureRate * 2)); // Up to 50 points for failure rate
        }
        
        // Deduct points for frequent state changes (instability)
        if (stateTransitionCount != null && stateTransitionCount > 5) {
            score -= Math.min(20, (stateTransitionCount - 5) * 2);
        }
        
        // Deduct points if circuit is open
        if ("OPEN".equals(state)) {
            score -= 30;
        } else if ("HALF_OPEN".equals(state)) {
            score -= 10;
        }
        
        return Math.max(0, score);
    }
    
    /**
     * Get human-readable state description
     */
    public String getStateDescription() {
        return switch (state) {
            case "CLOSED" -> "Normal operation - all calls allowed";
            case "OPEN" -> "Circuit open - calls rejected to prevent cascade failures";
            case "HALF_OPEN" -> "Testing mode - limited calls allowed to check recovery";
            default -> "Unknown state: " + state;
        };
    }
    
    /**
     * Create default healthy metrics
     */
    public static CircuitBreakerMetrics createHealthy() {
        return CircuitBreakerMetrics.builder()
            .state("CLOSED")
            .failureRate(0.0f)
            .successRate(100.0f)
            .numberOfCalls(0)
            .numberOfFailedCalls(0)
            .numberOfSuccessfulCalls(0)
            .numberOfNotPermittedCalls(0)
            .currentStateDurationMs(0L)
            .stateTransitionCount(0)
            .build();
    }
}