package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payment Resilience Result
 * 
 * Result of a resilience-protected payment operation, including
 * success status, result data, and resilience metadata.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResilienceResult<T> {
    
    /**
     * Unique operation identifier
     */
    private String operationId;
    
    /**
     * Whether the operation was successful
     */
    private Boolean success;
    
    /**
     * The actual result of the operation (if successful)
     */
    private T result;
    
    /**
     * Error type if operation failed
     */
    private String errorType;
    
    /**
     * Error message if operation failed
     */
    private String errorMessage;
    
    /**
     * Provider that processed the operation
     */
    private String provider;
    
    /**
     * Type of operation performed
     */
    private String operationType;
    
    /**
     * Timestamp when operation completed
     */
    private LocalDateTime timestamp;
    
    /**
     * Total execution time in milliseconds
     */
    private Long executionTimeMs;
    
    /**
     * Which resilience patterns were applied
     */
    private ResiliencePatterns appliedPatterns;
    
    /**
     * Circuit breaker state during operation
     */
    private String circuitBreakerState;
    
    /**
     * Whether operation hit rate limit
     */
    private Boolean rateLimited;
    
    /**
     * Whether operation timed out
     */
    private Boolean timedOut;
    
    /**
     * Whether operation used fallback
     */
    private Boolean usedFallback;
    
    /**
     * Retry attempt number (if retried)
     */
    private Integer retryAttempt;
    
    /**
     * Bulkhead queue time in milliseconds
     */
    private Long bulkheadQueueTimeMs;
    
    /**
     * Resilience metadata
     */
    private ResilienceMetadata metadata;
    
    /**
     * Resilience patterns that were applied
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResiliencePatterns {
        private Boolean circuitBreakerApplied;
        private Boolean rateLimitApplied;
        private Boolean bulkheadApplied;
        private Boolean timeoutApplied;
        private Boolean retryApplied;
        private Boolean fallbackApplied;
    }
    
    /**
     * Additional resilience metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResilienceMetadata {
        private String resilienceVersion;
        private Double circuitBreakerFailureRate;
        private Integer currentBulkheadPermissions;
        private Long rateLimitWindowRemaining;
        private String lastKnownProviderHealth;
    }
    
    /**
     * Check if operation was successful
     */
    public boolean isSuccessful() {
        return success != null && success;
    }
    
    /**
     * Check if operation failed due to resilience protection
     */
    public boolean isResilienceFailure() {
        return !isSuccessful() && (
            "CIRCUIT_BREAKER_OPEN".equals(errorType) ||
            "RATE_LIMIT_EXCEEDED".equals(errorType) ||
            "BULKHEAD_FULL".equals(errorType) ||
            "TIMEOUT".equals(errorType)
        );
    }
    
    /**
     * Check if operation failed due to business logic
     */
    public boolean isBusinessFailure() {
        return !isSuccessful() && !isResilienceFailure();
    }
    
    /**
     * Get failure category
     */
    public FailureCategory getFailureCategory() {
        if (isSuccessful()) {
            return null;
        }
        
        return switch (errorType) {
            case "CIRCUIT_BREAKER_OPEN" -> FailureCategory.CIRCUIT_BREAKER;
            case "RATE_LIMIT_EXCEEDED" -> FailureCategory.RATE_LIMIT;
            case "BULKHEAD_FULL" -> FailureCategory.BULKHEAD;
            case "TIMEOUT" -> FailureCategory.TIMEOUT;
            case "EXECUTION_ERROR" -> FailureCategory.EXECUTION;
            default -> FailureCategory.OTHER;
        };
    }
    
    /**
     * Create successful result
     */
    public static <T> PaymentResilienceResult<T> success(String operationId, T result) {
        return PaymentResilienceResult.<T>builder()
            .operationId(operationId)
            .success(true)
            .result(result)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create failed result
     */
    public static <T> PaymentResilienceResult<T> failure(String operationId, String errorType, String errorMessage) {
        return PaymentResilienceResult.<T>builder()
            .operationId(operationId)
            .success(false)
            .errorType(errorType)
            .errorMessage(errorMessage)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create rate limited result
     */
    public static <T> PaymentResilienceResult<T> rateLimited(String operationId, String provider) {
        return PaymentResilienceResult.<T>builder()
            .operationId(operationId)
            .success(false)
            .errorType("RATE_LIMIT_EXCEEDED")
            .errorMessage("Rate limit exceeded for provider: " + provider)
            .provider(provider)
            .rateLimited(true)
            .timestamp(LocalDateTime.now())
            .appliedPatterns(ResiliencePatterns.builder()
                .rateLimitApplied(true)
                .build())
            .build();
    }
    
    /**
     * Create circuit breaker open result
     */
    public static <T> PaymentResilienceResult<T> circuitBreakerOpen(String operationId, String provider) {
        return PaymentResilienceResult.<T>builder()
            .operationId(operationId)
            .success(false)
            .errorType("CIRCUIT_BREAKER_OPEN")
            .errorMessage("Circuit breaker is open for provider: " + provider)
            .provider(provider)
            .circuitBreakerState("OPEN")
            .timestamp(LocalDateTime.now())
            .appliedPatterns(ResiliencePatterns.builder()
                .circuitBreakerApplied(true)
                .build())
            .build();
    }
    
    /**
     * Create timeout result
     */
    public static <T> PaymentResilienceResult<T> timeout(String operationId, String provider, long timeoutMs) {
        return PaymentResilienceResult.<T>builder()
            .operationId(operationId)
            .success(false)
            .errorType("TIMEOUT")
            .errorMessage("Operation timed out after " + timeoutMs + "ms")
            .provider(provider)
            .timedOut(true)
            .executionTimeMs(timeoutMs)
            .timestamp(LocalDateTime.now())
            .appliedPatterns(ResiliencePatterns.builder()
                .timeoutApplied(true)
                .build())
            .build();
    }
    
    public enum FailureCategory {
        CIRCUIT_BREAKER,
        RATE_LIMIT,
        BULKHEAD,
        TIMEOUT,
        EXECUTION,
        OTHER
    }
}