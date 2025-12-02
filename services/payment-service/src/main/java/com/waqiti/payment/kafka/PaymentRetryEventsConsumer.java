package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentRetryEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.common.events.RetryExhaustedEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.RetryAttempt;
import com.waqiti.payment.domain.RetryStrategy;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.RetryAttemptRepository;
import com.waqiti.payment.service.PaymentRetryService;
import com.waqiti.payment.service.PaymentGatewayService;
import com.waqiti.payment.service.ExponentialBackoffService;
import com.waqiti.payment.service.RetryCircuitBreakerService;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.exception.RetryLimitExceededException;
import com.waqiti.payment.metrics.RetryMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.fraud.FraudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL Consumer for Payment Retry Events
 * 
 * Handles intelligent payment retry logic including:
 * - Exponential backoff with jitter
 * - Circuit breaker pattern for failed gateways
 * - Retry strategy selection based on failure type
 * - Gateway switching for retry attempts
 * - Retry limit enforcement and escalation
 * - Performance-based retry optimization
 * - Failed payment recovery workflows
 * 
 * This is CRITICAL for maximizing payment success rates.
 * Intelligent retry logic can improve success rates by 15-25%
 * while preventing cascading failures.
 * 
 * @author Waqiti Engineering  
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentRetryEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final RetryAttemptRepository retryAttemptRepository;
    private final PaymentRetryService retryService;
    private final PaymentGatewayService gatewayService;
    private final ExponentialBackoffService backoffService;
    private final RetryCircuitBreakerService circuitBreakerService;
    private final RetryMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final FraudService fraudService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Retry configuration constants
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final int INITIAL_RETRY_DELAY_MS = 2000;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final int MAX_RETRY_DELAY_MS = 300000; // 5 minutes
    private static final double JITTER_FACTOR = 0.1; // 10% jitter

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();
    
    // Failure type specific retry limits
    private static final Map<String, Integer> FAILURE_TYPE_RETRY_LIMITS = Map.of(
        "TIMEOUT", 3,
        "NETWORK_ERROR", 4,
        "GATEWAY_ERROR", 2,
        "INSUFFICIENT_FUNDS", 0, // No retry
        "INVALID_CARD", 0, // No retry
        "FRAUD_DETECTED", 0, // No retry
        "RATE_LIMIT", 5,
        "TEMPORARY_FAILURE", 3
    );
    
    // Circuit breaker thresholds
    private static final double CIRCUIT_BREAKER_FAILURE_THRESHOLD = 0.50; // 50%
    private static final int CIRCUIT_BREAKER_REQUEST_THRESHOLD = 20;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1 minute
    
    /**
     * Primary handler for payment retry events
     * Processes retry logic with intelligent backoff and circuit breaking
     */
    @KafkaListener(
        topics = "payment-retry-events",
        groupId = "payment-retry-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentRetryEvent(
            @Payload PaymentRetryEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("retry-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing payment retry event: paymentId={}, attempt={}, failureType={}, correlation={}",
            event.getPaymentId(), event.getAttemptNumber(), event.getFailureType(), correlationId);
        
        try {
            // Security and validation
            securityContext.validateFinancialOperation(event.getPaymentId(), "PAYMENT_RETRY");
            validateRetryEvent(event);
            
            // Check for fraud in retry patterns
            if (fraudService.isSuspiciousRetryPattern(event)) {
                log.warn("Suspicious retry pattern detected: paymentId={}", event.getPaymentId());
                handleSuspiciousRetryPattern(event, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on event type
            switch (event.getEventType()) {
                case RETRY_REQUEST:
                    processRetryRequest(event, correlationId);
                    break;
                case RETRY_SCHEDULED:
                    processScheduledRetry(event, correlationId);
                    break;
                case RETRY_GATEWAY_SWITCH:
                    processRetryGatewaySwitch(event, correlationId);
                    break;
                case RETRY_LIMIT_EXCEEDED:
                    processRetryLimitExceeded(event, correlationId);
                    break;
                case RETRY_SUCCESS:
                    processRetrySuccess(event, correlationId);
                    break;
                case RETRY_CIRCUIT_BREAKER:
                    processRetryCircuitBreaker(event, correlationId);
                    break;
                case RETRY_BACKOFF_UPDATE:
                    processRetryBackoffUpdate(event, correlationId);
                    break;
                default:
                    log.warn("Unknown retry event type: {}", event.getEventType());
                    break;
            }
            
            // Audit the retry operation
            auditService.logFinancialEvent(
                "RETRY_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "attemptNumber", event.getAttemptNumber(),
                    "failureType", event.getFailureType(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process retry event: paymentId={}, error={}",
                event.getPaymentId(), e.getMessage(), e);
            
            handleRetryEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Processes retry requests with intelligent strategy selection
     */
    private void processRetryRequest(PaymentRetryEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing retry request: paymentId={}, currentAttempt={}, failureType={}",
            payment.getId(), payment.getRetryAttempts(), event.getFailureType());
        
        // Check if payment is in retryable state
        if (!isPaymentRetryable(payment)) {
            log.warn("Payment is not in retryable state: paymentId={}, status={}",
                payment.getId(), payment.getStatus());
            return;
        }
        
        // Check retry limits based on failure type
        int maxRetries = getMaxRetriesForFailureType(event.getFailureType());
        if (payment.getRetryAttempts() >= maxRetries) {
            log.warn("Retry limit exceeded: paymentId={}, attempts={}, max={}",
                payment.getId(), payment.getRetryAttempts(), maxRetries);
            
            processRetryLimitExceeded(event, correlationId);
            return;
        }
        
        // Check circuit breaker for current gateway
        if (circuitBreakerService.isCircuitOpen(payment.getGatewayId())) {
            log.info("Circuit breaker open for gateway {}, attempting gateway switch",
                payment.getGatewayId());
            
            processRetryGatewaySwitch(event, correlationId);
            return;
        }
        
        // Calculate retry delay using exponential backoff with jitter
        long retryDelay = calculateRetryDelay(payment.getRetryAttempts(), event.getFailureType());
        
        // Create retry attempt record
        RetryAttempt retryAttempt = createRetryAttempt(payment, event, retryDelay, correlationId);
        retryAttemptRepository.save(retryAttempt);
        
        // Update payment retry information
        payment.setRetryAttempts(payment.getRetryAttempts() + 1);
        payment.setLastRetryTimestamp(LocalDateTime.now());
        payment.setNextRetryTimestamp(LocalDateTime.now().plusSeconds(retryDelay / 1000));
        payment.setRetryStrategy(determineRetryStrategy(event.getFailureType()));
        paymentRepository.save(payment);
        
        // Schedule the retry
        scheduleRetry(payment, retryAttempt, retryDelay, correlationId);
        
        // Update metrics
        metricsService.recordRetryAttempt(payment.getGatewayId(), event.getFailureType());
        
        log.info("Retry scheduled: paymentId={}, attempt={}, delay={}ms, strategy={}",
            payment.getId(), payment.getRetryAttempts(), retryDelay, payment.getRetryStrategy());
    }
    
    /**
     * Calculates retry delay using exponential backoff with jitter
     */
    private long calculateRetryDelay(int attemptNumber, String failureType) {
        // Base delay with exponential backoff
        long baseDelay = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attemptNumber));
        
        // Apply failure-type specific adjustments
        baseDelay = adjustDelayForFailureType(baseDelay, failureType);
        
        // Cap at maximum delay
        baseDelay = Math.min(baseDelay, MAX_RETRY_DELAY_MS);

        // Add jitter to prevent thundering herd
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        double jitter = (secureRandom.nextDouble() - 0.5) * 2 * JITTER_FACTOR;
        long finalDelay = (long) (baseDelay * (1 + jitter));

        return Math.max(finalDelay, 1000); // Minimum 1 second delay
    }
    
    /**
     * Adjusts delay based on failure type
     */
    private long adjustDelayForFailureType(long baseDelay, String failureType) {
        switch (failureType) {
            case "RATE_LIMIT":
                return baseDelay * 3; // Longer delay for rate limits
            case "TIMEOUT":
                return baseDelay * 2; // Moderate delay for timeouts
            case "NETWORK_ERROR":
                return baseDelay; // Standard delay
            case "GATEWAY_ERROR":
                return baseDelay / 2; // Shorter delay, might be transient
            case "TEMPORARY_FAILURE":
                return Math.min(baseDelay, 30000); // Cap at 30 seconds
            default:
                return baseDelay;
        }
    }
    
    /**
     * Determines optimal retry strategy based on failure type
     */
    private RetryStrategy determineRetryStrategy(String failureType) {
        switch (failureType) {
            case "TIMEOUT":
                return RetryStrategy.EXPONENTIAL_BACKOFF;
            case "NETWORK_ERROR":
                return RetryStrategy.EXPONENTIAL_BACKOFF;
            case "RATE_LIMIT":
                return RetryStrategy.LINEAR_BACKOFF;
            case "GATEWAY_ERROR":
                return RetryStrategy.IMMEDIATE_WITH_GATEWAY_SWITCH;
            case "TEMPORARY_FAILURE":
                return RetryStrategy.RAPID_RETRY;
            default:
                return RetryStrategy.EXPONENTIAL_BACKOFF;
        }
    }
    
    /**
     * Creates retry attempt record
     */
    private RetryAttempt createRetryAttempt(Payment payment, PaymentRetryEvent event, 
            long retryDelay, String correlationId) {
        
        return RetryAttempt.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .attemptNumber(payment.getRetryAttempts() + 1)
            .gatewayId(payment.getGatewayId())
            .failureType(event.getFailureType())
            .failureReason(event.getFailureReason())
            .retryStrategy(determineRetryStrategy(event.getFailureType()))
            .scheduledTime(LocalDateTime.now().plusSeconds(retryDelay / 1000))
            .retryDelay(retryDelay)
            .correlationId(correlationId)
            .status("SCHEDULED")
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Schedules retry asynchronously
     */
    @Async
    private void scheduleRetry(Payment payment, RetryAttempt retryAttempt, 
            long delayMs, String correlationId) {
        
        CompletableFuture.runAsync(() -> {
            try {
                // Wait for the calculated delay
                TimeUnit.MILLISECONDS.sleep(delayMs);
                
                // Create scheduled retry event
                PaymentRetryEvent scheduledEvent = PaymentRetryEvent.builder()
                    .paymentId(payment.getId())
                    .eventType("RETRY_SCHEDULED")
                    .attemptNumber(retryAttempt.getAttemptNumber())
                    .retryAttemptId(retryAttempt.getId())
                    .failureType(retryAttempt.getFailureType())
                    .gatewayId(payment.getGatewayId())
                    .correlationId(correlationId)
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("payment-retry-events", scheduledEvent);
                
                log.debug("Retry scheduled for execution: paymentId={}, attemptId={}",
                    payment.getId(), retryAttempt.getId());
                
            } catch (InterruptedException e) {
                log.warn("Retry scheduling interrupted: paymentId={}, attemptId={}",
                    payment.getId(), retryAttempt.getId());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to schedule retry: paymentId={}, error={}",
                    payment.getId(), e.getMessage(), e);
            }
        });
    }
    
    /**
     * Processes scheduled retry execution
     */
    private void processScheduledRetry(PaymentRetryEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Executing scheduled retry: paymentId={}, attempt={}, retryId={}",
            payment.getId(), event.getAttemptNumber(), event.getRetryAttemptId());
        
        // Update retry attempt status
        RetryAttempt retryAttempt = retryAttemptRepository.findById(event.getRetryAttemptId())
            .orElse(null);
        
        if (retryAttempt != null) {
            retryAttempt.setStatus("EXECUTING");
            retryAttempt.setExecutedAt(LocalDateTime.now());
            retryAttemptRepository.save(retryAttempt);
        }
        
        // Check if payment is still retryable
        if (!isPaymentRetryable(payment)) {
            log.warn("Payment no longer retryable during scheduled execution: {}",
                payment.getId());
            
            if (retryAttempt != null) {
                retryAttempt.setStatus("CANCELLED");
                retryAttemptRepository.save(retryAttempt);
            }
            return;
        }
        
        // Execute the retry
        try {
            boolean retrySuccess = retryService.executeRetry(payment, retryAttempt, correlationId);
            
            if (retrySuccess) {
                processRetrySuccess(event, correlationId);
            } else {
                // Retry failed, check if we should continue retrying
                if (payment.getRetryAttempts() < getMaxRetriesForFailureType(event.getFailureType())) {
                    // Schedule next retry
                    PaymentRetryEvent nextRetryEvent = PaymentRetryEvent.builder()
                        .paymentId(payment.getId())
                        .eventType("RETRY_REQUEST")
                        .attemptNumber(payment.getRetryAttempts() + 1)
                        .failureType(event.getFailureType())
                        .failureReason("Previous retry failed")
                        .gatewayId(payment.getGatewayId())
                        .correlationId(correlationId)
                        .timestamp(Instant.now())
                        .build();
                    
                    kafkaTemplate.send("payment-retry-events", nextRetryEvent);
                } else {
                    processRetryLimitExceeded(event, correlationId);
                }
            }
            
        } catch (Exception e) {
            log.error("Retry execution failed: paymentId={}, error={}",
                payment.getId(), e.getMessage(), e);
            
            if (retryAttempt != null) {
                retryAttempt.setStatus("FAILED");
                retryAttempt.setFailureReason(e.getMessage());
                retryAttemptRepository.save(retryAttempt);
            }
            
            // Record circuit breaker failure
            circuitBreakerService.recordFailure(payment.getGatewayId());
        }
    }
    
    /**
     * Processes retry with gateway switching
     */
    private void processRetryGatewaySwitch(PaymentRetryEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing retry with gateway switch: paymentId={}, currentGateway={}",
            payment.getId(), payment.getGatewayId());
        
        // Find alternative gateway
        String alternativeGateway = gatewayService.findAlternativeGateway(
            payment.getGatewayId(),
            payment.getPaymentMethod(),
            payment.getCurrency(),
            payment.getAmount()
        );
        
        if (alternativeGateway == null) {
            log.warn("No alternative gateway available for payment: {}", payment.getId());
            processRetryLimitExceeded(event, correlationId);
            return;
        }
        
        // Update payment with new gateway
        String originalGateway = payment.getGatewayId();
        payment.setGatewayId(alternativeGateway);
        payment.setGatewayName(gatewayService.getGatewayName(alternativeGateway));
        payment.setGatewaySwitchTimestamp(LocalDateTime.now());
        payment.setGatewaySwitchReason("Circuit breaker or retry optimization");
        paymentRepository.save(payment);
        
        // Create retry request with new gateway
        PaymentRetryEvent gatewayRetryEvent = PaymentRetryEvent.builder()
            .paymentId(payment.getId())
            .eventType("RETRY_REQUEST")
            .attemptNumber(event.getAttemptNumber())
            .failureType(event.getFailureType())
            .failureReason("Gateway switched for retry")
            .gatewayId(alternativeGateway)
            .originalGatewayId(originalGateway)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-retry-events", gatewayRetryEvent);
        
        log.info("Gateway switched for retry: paymentId={}, from={} to={}",
            payment.getId(), originalGateway, alternativeGateway);
        
        // Update metrics
        metricsService.recordGatewaySwitch(originalGateway, alternativeGateway, "RETRY");
    }
    
    /**
     * Processes retry limit exceeded scenarios
     */
    private void processRetryLimitExceeded(PaymentRetryEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.warn("Retry limit exceeded: paymentId={}, attempts={}, finalFailureType={}",
            payment.getId(), payment.getRetryAttempts(), event.getFailureType());
        
        // Update payment status to failed
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(String.format("Retry limit exceeded after %d attempts. Last failure: %s",
            payment.getRetryAttempts(), event.getFailureType()));
        payment.setFinalFailureTimestamp(LocalDateTime.now());
        paymentRepository.save(payment);
        
        // Publish retry exhausted event
        RetryExhaustedEvent exhaustedEvent = RetryExhaustedEvent.builder()
            .paymentId(payment.getId())
            .totalAttempts(payment.getRetryAttempts())
            .finalFailureType(event.getFailureType())
            .originalAmount(payment.getAmount())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("retry-exhausted-events", exhaustedEvent);
        
        // Publish payment status update
        publishPaymentStatusUpdate(payment, "RETRY_EXHAUSTED", correlationId);
        
        // Send notification for high-value failed payments
        if (payment.getAmount().compareTo(new BigDecimal("1000")) > 0) {
            notificationService.sendOperationalAlert(
                "High-Value Payment Failed After Retries",
                String.format("Payment %s ($%s) failed after %d retry attempts. Final failure: %s",
                    payment.getId(), payment.getAmount(), payment.getRetryAttempts(), 
                    event.getFailureType()),
                NotificationService.Priority.HIGH
            );
        }
        
        // Update circuit breaker and metrics
        circuitBreakerService.recordFailure(payment.getGatewayId());
        metricsService.recordRetryExhausted(payment.getGatewayId(), event.getFailureType());
    }
    
    /**
     * Processes successful retry
     */
    private void processRetrySuccess(PaymentRetryEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Retry successful: paymentId={}, finalAttempt={}", 
            payment.getId(), payment.getRetryAttempts());
        
        // Update payment status
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(LocalDateTime.now());
        payment.setRetrySuccessful(true);
        paymentRepository.save(payment);
        
        // Update retry attempt record
        if (event.getRetryAttemptId() != null) {
            RetryAttempt retryAttempt = retryAttemptRepository.findById(event.getRetryAttemptId())
                .orElse(null);
            
            if (retryAttempt != null) {
                retryAttempt.setStatus("SUCCESS");
                retryAttempt.setCompletedAt(LocalDateTime.now());
                retryAttemptRepository.save(retryAttempt);
            }
        }
        
        // Record circuit breaker success
        circuitBreakerService.recordSuccess(payment.getGatewayId());
        
        // Update metrics
        metricsService.recordRetrySuccess(payment.getGatewayId(), payment.getRetryAttempts());
        
        // Publish payment status update
        publishPaymentStatusUpdate(payment, "RETRY_SUCCESS", correlationId);
        
        log.info("Payment completed after retry: paymentId={}, attempts={}, gateway={}",
            payment.getId(), payment.getRetryAttempts(), payment.getGatewayId());
    }
    
    /**
     * Additional utility methods
     */
    private void processRetryCircuitBreaker(PaymentRetryEvent event, String correlationId) {
        log.info("Processing circuit breaker event: gatewayId={}, action={}",
            event.getGatewayId(), event.getCircuitBreakerAction());
        
        circuitBreakerService.handleCircuitBreakerEvent(
            event.getGatewayId(), 
            event.getCircuitBreakerAction()
        );
    }
    
    private void processRetryBackoffUpdate(PaymentRetryEvent event, String correlationId) {
        log.info("Processing backoff update: gatewayId={}, multiplier={}",
            event.getGatewayId(), event.getBackoffMultiplier());
        
        backoffService.updateBackoffParameters(
            event.getGatewayId(),
            event.getBackoffMultiplier(),
            event.getMaxDelay()
        );
    }
    
    /**
     * Validation and utility methods
     */
    private void validateRetryEvent(PaymentRetryEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getAttemptNumber() < 0) {
            throw new IllegalArgumentException("Attempt number must be non-negative");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private Payment getPaymentById(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(
                "Payment not found: " + paymentId));
    }
    
    private boolean isPaymentRetryable(Payment payment) {
        return payment.getStatus() == PaymentStatus.FAILED ||
               payment.getStatus() == PaymentStatus.PROCESSING_FAILED ||
               payment.getStatus() == PaymentStatus.TIMEOUT ||
               payment.getStatus() == PaymentStatus.GATEWAY_ERROR;
    }
    
    private int getMaxRetriesForFailureType(String failureType) {
        return FAILURE_TYPE_RETRY_LIMITS.getOrDefault(failureType, MAX_RETRY_ATTEMPTS);
    }
    
    private void handleSuspiciousRetryPattern(PaymentRetryEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        payment.setStatus(PaymentStatus.FRAUD_REVIEW);
        payment.setFraudReason("Suspicious retry pattern detected");
        paymentRepository.save(payment);
        
        notificationService.sendSecurityAlert(
            "Suspicious Retry Pattern",
            String.format("Unusual retry pattern detected for payment %s", event.getPaymentId()),
            NotificationService.Priority.HIGH
        );
    }
    
    private void publishPaymentStatusUpdate(Payment payment, String reason, String correlationId) {
        PaymentStatusUpdatedEvent statusEvent = PaymentStatusUpdatedEvent.builder()
            .paymentId(payment.getId())
            .status(payment.getStatus().toString())
            .reason(reason)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-status-updated-events", statusEvent);
    }
    
    private void handleRetryEventError(PaymentRetryEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-retry-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Retry Event Processing Failed",
            String.format("Failed to process retry event for payment %s: %s",
                event.getPaymentId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementRetryEventError(event.getEventType());
    }
}