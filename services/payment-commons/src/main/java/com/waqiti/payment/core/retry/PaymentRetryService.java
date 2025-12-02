package com.waqiti.payment.core.retry;

import com.waqiti.common.resilience.CircuitBreakerService;
import com.waqiti.payment.core.model.PaymentRequest;
import com.waqiti.payment.core.model.PaymentRetryPolicy;
import com.waqiti.payment.core.model.PaymentRetryResult;
import com.waqiti.payment.core.model.PaymentRetryStatus;
import com.waqiti.payment.core.audit.PaymentAuditService;
import com.waqiti.common.audit.dto.AuditEvent;
import com.waqiti.common.audit.dto.AuditEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Production-Ready Payment Retry Service
 * 
 * Provides comprehensive retry capabilities for payment operations with:
 * - Intelligent retry policies based on error types
 * - Exponential backoff with jitter
 * - Dead letter queue for failed retries
 * - Circuit breaker integration
 * - Comprehensive audit logging
 * - Retry attempt tracking and metrics
 * - Provider-specific retry strategies
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRetryService {

    private final PaymentAuditService auditService;
    private final CircuitBreakerService circuitBreakerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${payment.retry.max.attempts:3}")
    private int defaultMaxRetryAttempts;
    
    @Value("${payment.retry.base.delay.ms:1000}")
    private long baseDelayMs;
    
    @Value("${payment.retry.max.delay.ms:30000}")
    private long maxDelayMs;
    
    @Value("${payment.retry.jitter.factor:0.1}")
    private double jitterFactor;
    
    @Value("${payment.retry.enable.dead.letter:true}")
    private boolean deadLetterEnabled;
    
    private static final String RETRY_EVENTS_TOPIC = "payment-retry-events";
    private static final String DEAD_LETTER_TOPIC = "payment-dead-letter-queue";
    
    // Active retry tracking
    private final Map<String, PaymentRetryContext> activeRetries = new ConcurrentHashMap<>();
    
    // Retry queue for delayed processing
    private final DelayQueue<DelayedPaymentRetry> retryQueue = new DelayQueue<>();
    
    // Provider-specific retry policies
    private final Map<String, PaymentRetryPolicy> providerRetryPolicies = new HashMap<>();
    
    /**
     * Initialize default retry policies
     */
    @jakarta.annotation.PostConstruct
    public void initializeRetryPolicies() {
        // Stripe retry policy
        providerRetryPolicies.put("STRIPE", PaymentRetryPolicy.builder()
            .maxAttempts(5)
            .baseDelay(Duration.ofSeconds(1))
            .maxDelay(Duration.ofMinutes(5))
            .retryableErrors(Set.of("rate_limit_error", "api_connection_error", "api_error"))
            .nonRetryableErrors(Set.of("card_declined", "insufficient_funds", "invalid_request_error"))
            .backoffStrategy(PaymentRetryPolicy.BackoffStrategy.EXPONENTIAL_WITH_JITTER)
            .build());
        
        // PayPal retry policy
        providerRetryPolicies.put("PAYPAL", PaymentRetryPolicy.builder()
            .maxAttempts(4)
            .baseDelay(Duration.ofSeconds(2))
            .maxDelay(Duration.ofMinutes(3))
            .retryableErrors(Set.of("INTERNAL_SERVICE_ERROR", "TIMEOUT", "API_CONNECTION_ERROR"))
            .nonRetryableErrors(Set.of("AUTHORIZATION_FAILED", "INSUFFICIENT_FUNDS", "INVALID_ACCOUNT"))
            .backoffStrategy(PaymentRetryPolicy.BackoffStrategy.EXPONENTIAL)
            .build());
        
        // Bank transfer retry policy  
        providerRetryPolicies.put("BANK_TRANSFER", PaymentRetryPolicy.builder()
            .maxAttempts(3)
            .baseDelay(Duration.ofMinutes(1))
            .maxDelay(Duration.ofHours(1))
            .retryableErrors(Set.of("TEMPORARY_FAILURE", "NETWORK_ERROR", "PROCESSING_ERROR"))
            .nonRetryableErrors(Set.of("INVALID_ACCOUNT", "ACCOUNT_CLOSED", "COMPLIANCE_VIOLATION"))
            .backoffStrategy(PaymentRetryPolicy.BackoffStrategy.LINEAR)
            .build());
        
        // Crypto payment retry policy
        providerRetryPolicies.put("CRYPTO", PaymentRetryPolicy.builder()
            .maxAttempts(6)
            .baseDelay(Duration.ofSeconds(30))
            .maxDelay(Duration.ofMinutes(10))
            .retryableErrors(Set.of("NETWORK_CONGESTION", "MEMPOOL_FULL", "NODE_UNAVAILABLE"))
            .nonRetryableErrors(Set.of("INSUFFICIENT_BALANCE", "INVALID_ADDRESS", "DOUBLE_SPEND"))
            .backoffStrategy(PaymentRetryPolicy.BackoffStrategy.EXPONENTIAL_WITH_JITTER)
            .build());
        
        log.info("Initialized retry policies for {} providers", providerRetryPolicies.size());
    }
    
    /**
     * Calculate retry delay based on attempt number and error context
     */
    public Duration calculateRetryDelay(int attemptNumber, Object errorContext) {
        try {
            PaymentRetryPolicy policy = getRetryPolicy(errorContext);
            
            Duration delay = switch (policy.getBackoffStrategy()) {
                case LINEAR -> Duration.ofMillis(policy.getBaseDelay().toMillis() * attemptNumber);
                case EXPONENTIAL -> Duration.ofMillis(policy.getBaseDelay().toMillis() * (long) Math.pow(2, attemptNumber - 1));
                case EXPONENTIAL_WITH_JITTER -> calculateExponentialWithJitter(policy, attemptNumber);
                case FIXED -> policy.getBaseDelay();
            };
            
            // Cap at maximum delay
            if (delay.compareTo(policy.getMaxDelay()) > 0) {
                delay = policy.getMaxDelay();
            }
            
            log.debug("Calculated retry delay: {}ms for attempt {} using {} strategy", 
                delay.toMillis(), attemptNumber, policy.getBackoffStrategy());
            
            return delay;
            
        } catch (Exception e) {
            log.error("Error calculating retry delay, using default", e);
            return Duration.ofMillis(baseDelayMs * attemptNumber);
        }
    }
    
    /**
     * Calculate retry delay for exceptions
     */
    public Duration calculateRetryDelay(int attemptNumber, Exception exception) {
        try {
            // Categorize exception for retry policy selection
            String errorCategory = categorizeException(exception);
            
            Duration baseDelay = Duration.ofMillis(baseDelayMs);
            
            // Adjust delay based on exception type
            if (exception instanceof java.net.SocketTimeoutException) {
                baseDelay = Duration.ofSeconds(5); // Longer delay for timeouts
            } else if (exception instanceof java.net.ConnectException) {
                baseDelay = Duration.ofSeconds(3); // Medium delay for connection issues
            } else if (exception.getMessage().contains("rate") || exception.getMessage().contains("limit")) {
                baseDelay = Duration.ofSeconds(10); // Longer delay for rate limiting
            }
            
            // Apply exponential backoff with jitter
            long delayMs = (long) (baseDelay.toMillis() * Math.pow(2, attemptNumber - 1));
            
            // Add jitter to prevent thundering herd
            double jitter = 1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitterFactor;
            delayMs = (long) (delayMs * jitter);
            
            // Cap at maximum delay
            delayMs = Math.min(delayMs, maxDelayMs);
            
            log.debug("Calculated exception retry delay: {}ms for attempt {} ({})", 
                delayMs, attemptNumber, exception.getClass().getSimpleName());
            
            return Duration.ofMillis(delayMs);
            
        } catch (Exception e) {
            log.error("Error calculating exception retry delay, using default", e);
            return Duration.ofMillis(baseDelayMs * attemptNumber);
        }
    }
    
    /**
     * Determine if an error should be retried
     */
    public boolean shouldRetry(String paymentId, int currentAttempt, String errorCode, String provider) {
        try {
            PaymentRetryPolicy policy = getRetryPolicyForProvider(provider);
            
            // Check max attempts
            if (currentAttempt >= policy.getMaxAttempts()) {
                log.debug("Max retry attempts reached for payment: {} ({})", paymentId, currentAttempt);
                return false;
            }
            
            // Check if error is explicitly non-retryable
            if (policy.getNonRetryableErrors().contains(errorCode)) {
                log.debug("Error code {} is non-retryable for payment: {}", errorCode, paymentId);
                return false;
            }
            
            // Check if error is explicitly retryable
            if (policy.getRetryableErrors().contains(errorCode)) {
                log.debug("Error code {} is retryable for payment: {}", errorCode, paymentId);
                return true;
            }
            
            // Check circuit breaker state
            if (circuitBreakerService.isCircuitOpen("payment-provider-" + provider)) {
                log.debug("Circuit breaker open for provider {}, not retrying payment: {}", provider, paymentId);
                return false;
            }
            
            // Default to retrying unknown errors (conservative approach)
            log.debug("Unknown error code {}, defaulting to retry for payment: {}", errorCode, paymentId);
            return true;
            
        } catch (Exception e) {
            log.error("Error determining retry eligibility for payment: {}", paymentId, e);
            return false; // Fail safe
        }
    }
    
    /**
     * Schedule a payment for retry
     */
    public CompletableFuture<PaymentRetryResult> scheduleRetry(PaymentRequest paymentRequest, 
                                                              String errorCode, 
                                                              String errorMessage,
                                                              int currentAttempt,
                                                              String provider) {
        String paymentId = paymentRequest.getPaymentId();
        
        try {
            log.info("Scheduling retry for payment: {} (attempt {})", paymentId, currentAttempt + 1);
            
            // Check if retry is appropriate
            if (!shouldRetry(paymentId, currentAttempt, errorCode, provider)) {
                log.warn("Payment {} not eligible for retry, sending to dead letter queue", paymentId);
                
                if (deadLetterEnabled) {
                    sendToDeadLetterQueue(paymentRequest, errorCode, errorMessage, currentAttempt);
                }
                
                return CompletableFuture.completedFuture(PaymentRetryResult.builder()
                    .paymentId(paymentId)
                    .status(PaymentRetryStatus.NOT_RETRYABLE)
                    .finalError(errorCode + ": " + errorMessage)
                    .totalAttempts(currentAttempt)
                    .build());
            }
            
            // Calculate retry delay
            Duration retryDelay = calculateRetryDelay(currentAttempt + 1, errorCode);
            
            // Create retry context
            PaymentRetryContext retryContext = PaymentRetryContext.builder()
                .paymentId(paymentId)
                .paymentRequest(paymentRequest)
                .currentAttempt(currentAttempt + 1)
                .provider(provider)
                .lastError(errorCode)
                .lastErrorMessage(errorMessage)
                .nextRetryTime(LocalDateTime.now().plus(retryDelay))
                .retryPolicy(getRetryPolicyForProvider(provider))
                .createdAt(LocalDateTime.now())
                .build();
            
            // Store retry context
            activeRetries.put(paymentId, retryContext);
            
            // Schedule delayed retry
            DelayedPaymentRetry delayedRetry = new DelayedPaymentRetry(paymentId, retryDelay);
            retryQueue.offer(delayedRetry);
            
            // Audit retry scheduling
            auditService.auditRetryScheduled(paymentId, currentAttempt + 1, retryDelay, errorCode);
            
            // Publish retry event
            publishRetryEvent("RETRY_SCHEDULED", paymentId, retryContext);
            
            log.info("Retry scheduled for payment: {} in {}ms (attempt {})", 
                paymentId, retryDelay.toMillis(), currentAttempt + 1);
            
            return CompletableFuture.completedFuture(PaymentRetryResult.builder()
                .paymentId(paymentId)
                .status(PaymentRetryStatus.SCHEDULED)
                .nextRetryTime(retryContext.getNextRetryTime())
                .retryDelay(retryDelay)
                .attemptNumber(currentAttempt + 1)
                .build());
            
        } catch (Exception e) {
            log.error("Error scheduling retry for payment: {}", paymentId, e);
            
            return CompletableFuture.completedFuture(PaymentRetryResult.builder()
                .paymentId(paymentId)
                .status(PaymentRetryStatus.RETRY_FAILED)
                .finalError("Failed to schedule retry: " + e.getMessage())
                .totalAttempts(currentAttempt)
                .build());
        }
    }
    
    /**
     * Cancel scheduled retry
     */
    public boolean cancelRetry(String paymentId, String reason) {
        try {
            PaymentRetryContext context = activeRetries.remove(paymentId);
            
            if (context != null) {
                log.info("Cancelled retry for payment: {} (reason: {})", paymentId, reason);
                
                // Audit cancellation
                auditService.auditRetryCancelled(paymentId, context.getCurrentAttempt(), reason);
                
                // Publish cancellation event
                publishRetryEvent("RETRY_CANCELLED", paymentId, context);
                
                return true;
            } else {
                log.debug("No active retry found for payment: {}", paymentId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error cancelling retry for payment: {}", paymentId, e);
            return false;
        }
    }
    
    /**
     * Get retry status for a payment
     */
    public Optional<PaymentRetryContext> getRetryStatus(String paymentId) {
        return Optional.ofNullable(activeRetries.get(paymentId));
    }
    
    /**
     * Get all active retries
     */
    public Collection<PaymentRetryContext> getActiveRetries() {
        return new ArrayList<>(activeRetries.values());
    }
    
    /**
     * Process expired retries (should be called by scheduled task)
     */
    public void processExpiredRetries() {
        try {
            DelayedPaymentRetry expiredRetry;
            
            while ((expiredRetry = retryQueue.poll()) != null) {
                String paymentId = expiredRetry.getPaymentId();
                PaymentRetryContext context = activeRetries.get(paymentId);
                
                if (context != null) {
                    log.info("Processing expired retry for payment: {} (attempt {})", 
                        paymentId, context.getCurrentAttempt());
                    
                    // Publish retry ready event
                    publishRetryEvent("RETRY_READY", paymentId, context);
                    
                    // Note: The actual retry execution should be handled by the payment orchestrator
                    // This service only manages the retry scheduling and timing
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing expired retries", e);
        }
    }
    
    // Helper methods
    
    private PaymentRetryPolicy getRetryPolicy(Object errorContext) {
        // Extract provider from error context or use default
        String provider = extractProviderFromContext(errorContext);
        return getRetryPolicyForProvider(provider);
    }
    
    private PaymentRetryPolicy getRetryPolicyForProvider(String provider) {
        return providerRetryPolicies.getOrDefault(provider, createDefaultRetryPolicy());
    }
    
    private PaymentRetryPolicy createDefaultRetryPolicy() {
        return PaymentRetryPolicy.builder()
            .maxAttempts(defaultMaxRetryAttempts)
            .baseDelay(Duration.ofMillis(baseDelayMs))
            .maxDelay(Duration.ofMillis(maxDelayMs))
            .retryableErrors(Set.of("TIMEOUT", "CONNECTION_ERROR", "SERVICE_UNAVAILABLE"))
            .nonRetryableErrors(Set.of("INVALID_REQUEST", "AUTHORIZATION_FAILED", "INSUFFICIENT_FUNDS"))
            .backoffStrategy(PaymentRetryPolicy.BackoffStrategy.EXPONENTIAL_WITH_JITTER)
            .build();
    }
    
    private String extractProviderFromContext(Object errorContext) {
        // Extract provider information from error context
        if (errorContext instanceof Map) {
            Map<?, ?> contextMap = (Map<?, ?>) errorContext;
            return (String) contextMap.getOrDefault("provider", "DEFAULT");
        }
        return "DEFAULT";
    }
    
    private Duration calculateExponentialWithJitter(PaymentRetryPolicy policy, int attemptNumber) {
        long baseDelayMs = policy.getBaseDelay().toMillis();
        long exponentialDelay = (long) (baseDelayMs * Math.pow(2, attemptNumber - 1));
        
        // Add jitter
        double jitter = 1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitterFactor;
        long jitteredDelay = (long) (exponentialDelay * jitter);
        
        return Duration.ofMillis(jitteredDelay);
    }
    
    private String categorizeException(Exception exception) {
        String className = exception.getClass().getSimpleName();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        if (exception instanceof java.net.SocketTimeoutException || message.contains("timeout")) {
            return "TIMEOUT";
        } else if (exception instanceof java.net.ConnectException || message.contains("connection")) {
            return "CONNECTION_ERROR";
        } else if (message.contains("rate") || message.contains("limit")) {
            return "RATE_LIMIT";
        } else if (message.contains("unauthorized") || message.contains("forbidden")) {
            return "AUTHORIZATION_ERROR";
        } else {
            return "UNKNOWN_ERROR";
        }
    }
    
    private void sendToDeadLetterQueue(PaymentRequest paymentRequest, String errorCode, 
                                     String errorMessage, int totalAttempts) {
        try {
            Map<String, Object> deadLetterEvent = new HashMap<>();
            deadLetterEvent.put("paymentId", paymentRequest.getPaymentId());
            deadLetterEvent.put("originalRequest", paymentRequest);
            deadLetterEvent.put("finalError", errorCode);
            deadLetterEvent.put("finalErrorMessage", errorMessage);
            deadLetterEvent.put("totalAttempts", totalAttempts);
            deadLetterEvent.put("timestamp", LocalDateTime.now().toString());
            deadLetterEvent.put("reason", "MAX_RETRIES_EXCEEDED");
            
            kafkaTemplate.send(DEAD_LETTER_TOPIC, paymentRequest.getPaymentId(), deadLetterEvent);
            
            log.info("Payment {} sent to dead letter queue after {} attempts", 
                paymentRequest.getPaymentId(), totalAttempts);
            
        } catch (Exception e) {
            log.error("Error sending payment {} to dead letter queue", paymentRequest.getPaymentId(), e);
        }
    }
    
    private void publishRetryEvent(String eventType, String paymentId, PaymentRetryContext context) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("paymentId", paymentId);
            event.put("attemptNumber", context.getCurrentAttempt());
            event.put("provider", context.getProvider());
            event.put("nextRetryTime", context.getNextRetryTime().toString());
            event.put("lastError", context.getLastError());
            event.put("timestamp", LocalDateTime.now().toString());
            
            kafkaTemplate.send(RETRY_EVENTS_TOPIC, paymentId, event);
            
        } catch (Exception e) {
            log.error("Error publishing retry event: {} for payment: {}", eventType, paymentId, e);
        }
    }
    
    // Inner classes
    
    private static class DelayedPaymentRetry implements Delayed {
        private final String paymentId;
        private final long executeTime;
        
        public DelayedPaymentRetry(String paymentId, Duration delay) {
            this.paymentId = paymentId;
            this.executeTime = System.currentTimeMillis() + delay.toMillis();
        }
        
        public String getPaymentId() {
            return paymentId;
        }
        
        @Override
        public long getDelay(TimeUnit unit) {
            long remaining = executeTime - System.currentTimeMillis();
            return unit.convert(remaining, TimeUnit.MILLISECONDS);
        }
        
        @Override
        public int compareTo(Delayed other) {
            return Long.compare(this.executeTime, ((DelayedPaymentRetry) other).executeTime);
        }
    }
}