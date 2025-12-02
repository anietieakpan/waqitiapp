package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Payment Retry Result
 * 
 * Represents the outcome of a payment retry scheduling or execution operation.
 * Contains status information, timing details, and retry metadata.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRetryResult {
    
    /**
     * Payment ID being retried
     */
    private String paymentId;
    
    /**
     * Current retry status
     */
    private PaymentRetryStatus status;
    
    /**
     * Current attempt number (1-based)
     */
    private Integer attemptNumber;
    
    /**
     * Total number of attempts made so far
     */
    private Integer totalAttempts;
    
    /**
     * Maximum attempts allowed
     */
    private Integer maxAttempts;
    
    /**
     * Timestamp when next retry is scheduled
     */
    private LocalDateTime nextRetryTime;
    
    /**
     * Delay until next retry
     */
    private Duration retryDelay;
    
    /**
     * Provider being used for retry
     */
    private String provider;
    
    /**
     * Last error that triggered the retry
     */
    private String lastError;
    
    /**
     * Last error message
     */
    private String lastErrorMessage;
    
    /**
     * Final error when retry is not possible
     */
    private String finalError;
    
    /**
     * Retry policy applied
     */
    private String retryPolicyName;
    
    /**
     * When this result was created
     */
    private LocalDateTime timestamp;
    
    /**
     * Whether this is the final attempt
     */
    private Boolean isFinalAttempt;
    
    /**
     * Reason why retry was not scheduled (if applicable)
     */
    private String noRetryReason;
    
    /**
     * Check if retry was successful
     */
    public boolean isRetryScheduled() {
        return status == PaymentRetryStatus.SCHEDULED;
    }
    
    /**
     * Check if retry is not possible
     */
    public boolean isRetryNotPossible() {
        return status == PaymentRetryStatus.NOT_RETRYABLE ||
               status == PaymentRetryStatus.MAX_ATTEMPTS_REACHED ||
               status == PaymentRetryStatus.RETRY_FAILED;
    }
    
    /**
     * Check if retry is in progress
     */
    public boolean isRetryInProgress() {
        return status == PaymentRetryStatus.IN_PROGRESS ||
               status == PaymentRetryStatus.SCHEDULED;
    }
    
    /**
     * Get remaining retry attempts
     */
    public int getRemainingAttempts() {
        if (maxAttempts == null || attemptNumber == null) {
            return 0;
        }
        return Math.max(0, maxAttempts - attemptNumber);
    }
    
    /**
     * Check if this is the last possible attempt
     */
    public boolean isLastAttempt() {
        return getRemainingAttempts() <= 1;
    }
    
    /**
     * Create a successful retry scheduling result
     */
    public static PaymentRetryResult scheduled(String paymentId, int attemptNumber, 
                                             LocalDateTime nextRetryTime, Duration retryDelay) {
        return PaymentRetryResult.builder()
            .paymentId(paymentId)
            .status(PaymentRetryStatus.SCHEDULED)
            .attemptNumber(attemptNumber)
            .nextRetryTime(nextRetryTime)
            .retryDelay(retryDelay)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create a result indicating retry is not possible
     */
    public static PaymentRetryResult notRetryable(String paymentId, String reason) {
        return PaymentRetryResult.builder()
            .paymentId(paymentId)
            .status(PaymentRetryStatus.NOT_RETRYABLE)
            .finalError(reason)
            .noRetryReason(reason)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create a result indicating max attempts reached
     */
    public static PaymentRetryResult maxAttemptsReached(String paymentId, int totalAttempts) {
        return PaymentRetryResult.builder()
            .paymentId(paymentId)
            .status(PaymentRetryStatus.MAX_ATTEMPTS_REACHED)
            .totalAttempts(totalAttempts)
            .finalError("Maximum retry attempts exceeded")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create a result indicating retry operation failed
     */
    public static PaymentRetryResult retryFailed(String paymentId, String error) {
        return PaymentRetryResult.builder()
            .paymentId(paymentId)
            .status(PaymentRetryStatus.RETRY_FAILED)
            .finalError(error)
            .timestamp(LocalDateTime.now())
            .build();
    }
}