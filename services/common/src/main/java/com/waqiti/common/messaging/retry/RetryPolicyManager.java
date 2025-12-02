package com.waqiti.common.messaging.retry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRITICAL PRODUCTION FIX - RetryPolicyManager
 * Manages retry policies for dead letter queue message processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetryPolicyManager {
    
    private final Map<String, RetryPolicy> retryPolicies = new ConcurrentHashMap<>();
    
    /**
     * Get retry policy for message type
     */
    public RetryPolicy getRetryPolicy(String messageType) {
        return retryPolicies.computeIfAbsent(messageType, this::createDefaultRetryPolicy);
    }
    
    /**
     * Check if message should be retried
     */
    public boolean shouldRetry(String messageType, int currentAttempts, Throwable lastError) {
        RetryPolicy policy = getRetryPolicy(messageType);
        return currentAttempts < policy.getMaxAttempts() && 
               policy.isRetryableException(lastError);
    }
    
    /**
     * Calculate next retry delay
     */
    public long getNextRetryDelay(String messageType, int attemptNumber) {
        RetryPolicy policy = getRetryPolicy(messageType);
        return policy.calculateDelay(attemptNumber);
    }
    
    private RetryPolicy createDefaultRetryPolicy(String messageType) {
        return RetryPolicy.builder()
            .messageType(messageType)
            .maxAttempts(3)
            .baseDelayMs(1000)
            .backoffMultiplier(2.0)
            .maxDelayMs(30000)
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RetryPolicy {
        private String messageType;
        private int maxAttempts;
        private long baseDelayMs;
        private double backoffMultiplier;
        private long maxDelayMs;
        
        public boolean isRetryableException(Throwable error) {
            // Network errors, timeouts are retryable
            // Business logic errors are not
            return !(error instanceof IllegalArgumentException) &&
                   !(error instanceof SecurityException);
        }
        
        public long calculateDelay(int attemptNumber) {
            long delay = (long) (baseDelayMs * Math.pow(backoffMultiplier, attemptNumber - 1));
            return Math.min(delay, maxDelayMs);
        }
    }
}