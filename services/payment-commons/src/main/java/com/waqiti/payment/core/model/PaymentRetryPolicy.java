package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Payment Retry Policy Configuration
 * 
 * Defines retry behavior and policies for payment operations
 * including maximum attempts, backoff strategies, and error classifications.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRetryPolicy {
    
    /**
     * Maximum number of retry attempts
     */
    private Integer maxAttempts;
    
    /**
     * Base delay between retry attempts
     */
    private Duration baseDelay;
    
    /**
     * Maximum delay cap for retry attempts
     */
    private Duration maxDelay;
    
    /**
     * Backoff strategy for calculating delays
     */
    private BackoffStrategy backoffStrategy;
    
    /**
     * Set of error codes that should trigger retries
     */
    private Set<String> retryableErrors;
    
    /**
     * Set of error codes that should never be retried
     */
    private Set<String> nonRetryableErrors;
    
    /**
     * Whether to apply jitter to prevent thundering herd
     */
    @Builder.Default
    private Boolean enableJitter = true;
    
    /**
     * Jitter factor (0.0 to 1.0)
     */
    @Builder.Default
    private Double jitterFactor = 0.1;
    
    /**
     * Available backoff strategies
     */
    public enum BackoffStrategy {
        /**
         * Fixed delay between attempts
         */
        FIXED,
        
        /**
         * Linear increase in delay (delay * attempt)
         */
        LINEAR,
        
        /**
         * Exponential backoff (delay * 2^attempt)
         */
        EXPONENTIAL,
        
        /**
         * Exponential backoff with jitter to prevent thundering herd
         */
        EXPONENTIAL_WITH_JITTER
    }
    
    /**
     * Check if an error code is retryable
     */
    public boolean isRetryable(String errorCode) {
        if (errorCode == null) return false;
        
        // Explicitly non-retryable errors take precedence
        if (nonRetryableErrors != null && nonRetryableErrors.contains(errorCode)) {
            return false;
        }
        
        // Check if explicitly retryable
        if (retryableErrors != null && retryableErrors.contains(errorCode)) {
            return true;
        }
        
        // Default behavior for unknown errors
        return false;
    }
    
    /**
     * Calculate delay for a specific attempt using the configured strategy
     */
    public Duration calculateDelay(int attemptNumber) {
        if (baseDelay == null) {
            return Duration.ofSeconds(1);
        }
        
        Duration delay = switch (backoffStrategy) {
            case FIXED -> baseDelay;
            case LINEAR -> Duration.ofMillis(baseDelay.toMillis() * attemptNumber);
            case EXPONENTIAL -> Duration.ofMillis((long) (baseDelay.toMillis() * Math.pow(2, attemptNumber - 1)));
            case EXPONENTIAL_WITH_JITTER -> calculateExponentialWithJitter(attemptNumber);
        };
        
        // Apply maximum delay cap
        if (maxDelay != null && delay.compareTo(maxDelay) > 0) {
            delay = maxDelay;
        }
        
        return delay;
    }
    
    private Duration calculateExponentialWithJitter(int attemptNumber) {
        long exponentialDelay = (long) (baseDelay.toMillis() * Math.pow(2, attemptNumber - 1));
        
        if (enableJitter && jitterFactor != null) {
            // Use ThreadLocalRandom for secure and thread-safe random generation
            double jitter = 1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitterFactor;
            exponentialDelay = (long) (exponentialDelay * jitter);
        }
        
        return Duration.ofMillis(exponentialDelay);
    }
    
    /**
     * Create default retry policy
     */
    public static PaymentRetryPolicy createDefault() {
        return PaymentRetryPolicy.builder()
            .maxAttempts(3)
            .baseDelay(Duration.ofSeconds(1))
            .maxDelay(Duration.ofMinutes(5))
            .backoffStrategy(BackoffStrategy.EXPONENTIAL_WITH_JITTER)
            .retryableErrors(Set.of("TIMEOUT", "CONNECTION_ERROR", "SERVICE_UNAVAILABLE", "RATE_LIMIT"))
            .nonRetryableErrors(Set.of("INVALID_REQUEST", "AUTHORIZATION_FAILED", "INSUFFICIENT_FUNDS"))
            .enableJitter(true)
            .jitterFactor(0.1)
            .build();
    }
}