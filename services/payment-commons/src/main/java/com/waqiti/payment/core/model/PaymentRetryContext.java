package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment Retry Context
 * 
 * Contains all contextual information for a payment retry operation,
 * including the original request, retry state, and execution metadata.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRetryContext {
    
    /**
     * Unique identifier for the payment being retried
     */
    private String paymentId;
    
    /**
     * Original payment request
     */
    private PaymentRequest paymentRequest;
    
    /**
     * Current retry attempt number (1-based)
     */
    private Integer currentAttempt;
    
    /**
     * Provider being used for this retry
     */
    private String provider;
    
    /**
     * Last error code that triggered the retry
     */
    private String lastError;
    
    /**
     * Last error message
     */
    private String lastErrorMessage;
    
    /**
     * When the next retry should be executed
     */
    private LocalDateTime nextRetryTime;
    
    /**
     * Retry policy being applied
     */
    private PaymentRetryPolicy retryPolicy;
    
    /**
     * When this retry context was created
     */
    private LocalDateTime createdAt;
    
    /**
     * When this retry context was last updated
     */
    private LocalDateTime updatedAt;
    
    /**
     * Current status of the retry
     */
    private PaymentRetryStatus status;
    
    /**
     * Additional metadata for the retry
     */
    private Map<String, Object> metadata;
    
    /**
     * Reason for the retry (human readable)
     */
    private String retryReason;
    
    /**
     * Original error that first triggered retry sequence
     */
    private String originalError;
    
    /**
     * History of all retry attempts
     */
    private java.util.List<RetryAttempt> attemptHistory;
    
    /**
     * Whether this retry should be prioritized
     */
    @Builder.Default
    private Boolean highPriority = false;
    
    /**
     * Maximum delay allowed for this retry
     */
    private java.time.Duration maxDelay;
    
    /**
     * Tags for categorizing this retry
     */
    private java.util.Set<String> tags;
    
    /**
     * Check if retry is ready for execution
     */
    public boolean isReadyForExecution() {
        return nextRetryTime != null && 
               LocalDateTime.now().isAfter(nextRetryTime) &&
               status == PaymentRetryStatus.SCHEDULED;
    }
    
    /**
     * Check if retry has exceeded maximum attempts
     */
    public boolean hasExceededMaxAttempts() {
        return retryPolicy != null && 
               currentAttempt != null &&
               currentAttempt >= retryPolicy.getMaxAttempts();
    }
    
    /**
     * Get time until next retry
     */
    public java.time.Duration getTimeUntilNextRetry() {
        if (nextRetryTime == null) {
            return java.time.Duration.ZERO;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(nextRetryTime)) {
            return java.time.Duration.ZERO;
        }
        
        return java.time.Duration.between(now, nextRetryTime);
    }
    
    /**
     * Update the retry context for the next attempt
     */
    public void prepareForNextAttempt(String newError, String newErrorMessage) {
        this.lastError = newError;
        this.lastErrorMessage = newErrorMessage;
        this.currentAttempt = (this.currentAttempt != null) ? this.currentAttempt + 1 : 1;
        this.updatedAt = LocalDateTime.now();
        
        // Calculate next retry time based on policy
        if (retryPolicy != null) {
            java.time.Duration delay = retryPolicy.calculateDelay(currentAttempt);
            this.nextRetryTime = LocalDateTime.now().plus(delay);
        }
        
        // Add to attempt history
        if (attemptHistory == null) {
            attemptHistory = new java.util.ArrayList<>();
        }
        
        attemptHistory.add(RetryAttempt.builder()
            .attemptNumber(currentAttempt - 1) // Previous attempt
            .timestamp(LocalDateTime.now())
            .errorCode(lastError)
            .errorMessage(lastErrorMessage)
            .provider(provider)
            .build());
    }
    
    /**
     * Individual retry attempt record
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryAttempt {
        private Integer attemptNumber;
        private LocalDateTime timestamp;
        private String errorCode;
        private String errorMessage;
        private String provider;
        private java.time.Duration processingTime;
        private String outcome;
    }
    
    /**
     * Create initial retry context
     */
    public static PaymentRetryContext createInitial(PaymentRequest paymentRequest, 
                                                   String provider, 
                                                   String errorCode, 
                                                   String errorMessage,
                                                   PaymentRetryPolicy retryPolicy) {
        return PaymentRetryContext.builder()
            .paymentId(paymentRequest.getPaymentId())
            .paymentRequest(paymentRequest)
            .currentAttempt(1)
            .provider(provider)
            .lastError(errorCode)
            .lastErrorMessage(errorMessage)
            .originalError(errorCode)
            .retryPolicy(retryPolicy)
            .status(PaymentRetryStatus.SCHEDULED)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .nextRetryTime(LocalDateTime.now().plus(retryPolicy.calculateDelay(1)))
            .retryReason("Payment failed with error: " + errorCode)
            .attemptHistory(new java.util.ArrayList<>())
            .build();
    }
}