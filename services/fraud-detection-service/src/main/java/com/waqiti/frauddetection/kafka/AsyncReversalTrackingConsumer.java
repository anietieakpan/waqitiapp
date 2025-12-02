package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for async reversal tracking events
 * Handles tracking and monitoring of asynchronous transaction reversals
 * Features enhanced DLQ and retry capabilities for critical reversal operations
 *
 * Critical for: Reversal tracking, fraud mitigation, transaction integrity
 * SLA: Must process tracking events within 15 seconds for accurate status updates
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncReversalTrackingConsumer {

    private final AsyncReversalService asyncReversalService;
    private final ReversalTrackingService reversalTrackingService;
    private final FraudDetectionService fraudDetectionService;
    private final TransactionReversalService transactionReversalService;
    private final CustomerCommunicationService customerCommunicationService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter retryCounter;
    private Counter dlqCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("async_reversal_tracking_processed_total")
            .description("Total number of successfully processed async reversal tracking events")
            .register(meterRegistry);
        errorCounter = Counter.builder("async_reversal_tracking_errors_total")
            .description("Total number of async reversal tracking processing errors")
            .register(meterRegistry);
        retryCounter = Counter.builder("async_reversal_tracking_retries_total")
            .description("Total number of async reversal tracking retry attempts")
            .register(meterRegistry);
        dlqCounter = Counter.builder("async_reversal_tracking_dlq_total")
            .description("Total number of async reversal tracking events sent to DLQ")
            .register(meterRegistry);
        processingTimer = Timer.builder("async_reversal_tracking_processing_duration")
            .description("Time taken to process async reversal tracking events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"async-reversal-tracking", "fraud-reversal-tracking", "reversal-status-updates"},
        groupId = "fraud-async-reversal-tracking-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        retryTopicSuffix = "-retry",
        dltTopicSuffix = ".DLQ"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "async-reversal-tracking", fallbackMethod = "handleAsyncReversalTrackingEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAsyncReversalTrackingEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("async-rev-track-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        // Check if this is a retry attempt
        boolean isRetry = topic != null && topic.contains("-retry");
        if (isRetry) {
            retryCounter.increment();
            log.info("Processing retry attempt for reversal tracking: topic={}, eventId={}", topic, event.getId());
        }

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing async reversal tracking: id={}, type={}, reversalId={}",
                event.getId(), event.getEventType(), event.getData().get("reversalId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String reversalId = (String) event.getData().get("reversalId");
            String transactionId = (String) event.getData().get("transactionId");
            String userId = (String) event.getData().get("userId");

            switch (event.getEventType()) {
                case "REVERSAL_INITIATED":
                    handleReversalInitiated(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "REVERSAL_IN_PROGRESS":
                    handleReversalInProgress(event, reversalId, transactionId, correlationId);
                    break;

                case "REVERSAL_COMPLETED":
                    handleReversalCompleted(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "REVERSAL_FAILED":
                    handleReversalFailed(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "REVERSAL_PARTIALLY_COMPLETED":
                    handleReversalPartiallyCompleted(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "REVERSAL_CANCELLED":
                    handleReversalCancelled(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "REVERSAL_TIMEOUT":
                    handleReversalTimeout(event, reversalId, transactionId, correlationId);
                    break;

                case "REVERSAL_RETRY_INITIATED":
                    handleReversalRetryInitiated(event, reversalId, transactionId, correlationId);
                    break;

                case "REVERSAL_STATUS_UPDATE":
                    handleReversalStatusUpdate(event, reversalId, transactionId, correlationId);
                    break;

                default:
                    log.warn("Unknown async reversal tracking event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFinancialEvent("ASYNC_REVERSAL_TRACKING_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "reversalId", reversalId,
                    "transactionId", transactionId, "correlationId", correlationId,
                    "isRetry", isRetry, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process async reversal tracking event: {}", e.getMessage(), e);

            // Send fallback event with retry information
            kafkaTemplate.send("async-reversal-tracking-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "isRetry", isRetry, "topic", topic,
                "retryCount", isRetry ? extractRetryNumber(topic) : 0, "maxRetries", 5));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAsyncReversalTrackingEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("async-rev-track-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for async reversal tracking: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to specific fallback queue
        kafkaTemplate.send("async-reversal-tracking-circuit-breaker-fallback", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "topic", topic,
            "timestamp", Instant.now()));

        // Send high priority notification
        try {
            notificationService.sendOperationalAlert(
                "Async Reversal Tracking Consumer Circuit Breaker Triggered",
                String.format("Critical: Reversal tracking event %s failed with circuit breaker: %s",
                    event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAsyncReversalTrackingEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) String stackTrace) {

        dlqCounter.increment();
        String correlationId = String.format("dlt-async-rev-track-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Async reversal tracking permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Extract reversal information for critical processing
        String reversalId = (String) event.getData().get("reversalId");
        String transactionId = (String) event.getData().get("transactionId");
        String userId = (String) event.getData().get("userId");

        // Save to dead letter store with enhanced details
        auditService.logFinancialEvent("ASYNC_REVERSAL_TRACKING_DLT_EVENT", userId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "reversalId", reversalId,
                "transactionId", transactionId, "correlationId", correlationId,
                "stackTrace", stackTrace != null ? stackTrace : "N/A",
                "requiresUrgentIntervention", true, "timestamp", Instant.now()));

        // Mark reversal for manual intervention
        try {
            reversalTrackingService.markReversalForManualIntervention(reversalId, transactionId,
                "DLT_PROCESSING_FAILURE", exceptionMessage);
        } catch (Exception e) {
            log.error("Failed to mark reversal for manual intervention: {}", e.getMessage());
        }

        // Send emergency alert for critical DLT events
        try {
            notificationService.sendEmergencyAlert(
                "CRITICAL: Async Reversal Tracking Dead Letter Event",
                String.format("URGENT: Reversal tracking event %s (reversal: %s, transaction: %s) sent to DLT: %s",
                    event.getId(), reversalId, transactionId, exceptionMessage),
                Map.of("eventId", event.getId(), "reversalId", reversalId,
                    "transactionId", transactionId, "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }

        // Create manual intervention task
        try {
            kafkaTemplate.send("manual-intervention-tasks", Map.of(
                "taskType", "REVERSAL_TRACKING_DLT",
                "reversalId", reversalId,
                "transactionId", transactionId,
                "userId", userId,
                "priority", "CRITICAL",
                "description", "Reversal tracking event failed and sent to DLT",
                "errorDetails", exceptionMessage,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } catch (Exception e) {
            log.error("Failed to create manual intervention task: {}", e.getMessage());
        }
    }

    private void handleReversalInitiated(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        BigDecimal amount = new BigDecimal(event.getData().get("amount").toString());
        String reason = (String) event.getData().get("reason");
        log.info("Reversal initiated: reversalId={}, transactionId={}, amount={}, reason={}",
            reversalId, transactionId, amount, reason);

        // Start tracking the reversal
        reversalTrackingService.startReversalTracking(reversalId, transactionId, userId, amount, reason);

        // Create tracking record
        reversalTrackingService.createTrackingRecord(reversalId, "INITIATED", correlationId);

        // Send initial notification
        customerCommunicationService.sendReversalInitiatedNotification(userId, reversalId, transactionId);

        log.info("Reversal tracking initiated: reversalId={}", reversalId);
    }

    private void handleReversalInProgress(GenericKafkaEvent event, String reversalId, String transactionId, String correlationId) {
        String progressStage = (String) event.getData().get("progressStage");
        Integer progressPercentage = (Integer) event.getData().get("progressPercentage");
        log.info("Reversal in progress: reversalId={}, stage={}, progress={}%",
            reversalId, progressStage, progressPercentage);

        // Update tracking status
        reversalTrackingService.updateTrackingStatus(reversalId, "IN_PROGRESS", progressStage, progressPercentage);

        // Monitor progress and identify potential issues
        reversalTrackingService.monitorReversalProgress(reversalId, progressStage, progressPercentage);

        log.info("Reversal progress updated: reversalId={}, stage={}", reversalId, progressStage);
    }

    private void handleReversalCompleted(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        BigDecimal reversedAmount = new BigDecimal(event.getData().get("reversedAmount").toString());
        log.info("Reversal completed: reversalId={}, transactionId={}, amount={}", reversalId, transactionId, reversedAmount);

        // Complete tracking
        reversalTrackingService.completeReversalTracking(reversalId, reversedAmount);

        // Update fraud detection models
        fraudDetectionService.recordSuccessfulReversal(transactionId, reversedAmount);

        // Send completion notification
        customerCommunicationService.sendReversalCompletedNotification(userId, reversalId, reversedAmount);

        // Send completion event to other systems
        kafkaTemplate.send("reversal-completion-events", Map.of(
            "reversalId", reversalId,
            "transactionId", transactionId,
            "userId", userId,
            "reversedAmount", reversedAmount,
            "completedAt", Instant.now(),
            "correlationId", correlationId
        ));

        log.info("Reversal completion processed: reversalId={}", reversalId);
    }

    private void handleReversalFailed(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String failureReason = (String) event.getData().get("failureReason");
        String errorCode = (String) event.getData().get("errorCode");
        log.error("Reversal failed: reversalId={}, transactionId={}, reason={}, errorCode={}",
            reversalId, transactionId, failureReason, errorCode);

        // Record failure
        reversalTrackingService.recordReversalFailure(reversalId, failureReason, errorCode);

        // Analyze failure for potential retry
        boolean canRetry = reversalTrackingService.analyzeFailureForRetry(reversalId, failureReason, errorCode);

        if (canRetry) {
            // Schedule retry
            reversalTrackingService.scheduleReversalRetry(reversalId, correlationId);
        } else {
            // Send failure notification
            customerCommunicationService.sendReversalFailedNotification(userId, reversalId, failureReason);

            // Escalate for manual review
            fraudNotificationService.sendReversalFailureAlert(reversalId, transactionId, failureReason, correlationId);
        }

        log.info("Reversal failure processed: reversalId={}, canRetry={}", reversalId, canRetry);
    }

    private void handleReversalPartiallyCompleted(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        BigDecimal partialAmount = new BigDecimal(event.getData().get("partialAmount").toString());
        BigDecimal remainingAmount = new BigDecimal(event.getData().get("remainingAmount").toString());
        log.info("Reversal partially completed: reversalId={}, partial={}, remaining={}",
            reversalId, partialAmount, remainingAmount);

        // Update tracking with partial completion
        reversalTrackingService.recordPartialCompletion(reversalId, partialAmount, remainingAmount);

        // Send partial completion notification
        customerCommunicationService.sendPartialReversalNotification(userId, reversalId, partialAmount, remainingAmount);

        // Continue tracking remaining amount
        reversalTrackingService.continueTrackingRemaining(reversalId, remainingAmount);

        log.info("Partial reversal processed: reversalId={}", reversalId);
    }

    private void handleReversalCancelled(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String cancellationReason = (String) event.getData().get("cancellationReason");
        log.info("Reversal cancelled: reversalId={}, transactionId={}, reason={}", reversalId, transactionId, cancellationReason);

        // Cancel tracking
        reversalTrackingService.cancelReversalTracking(reversalId, cancellationReason);

        // Send cancellation notification
        customerCommunicationService.sendReversalCancelledNotification(userId, reversalId, cancellationReason);

        log.info("Reversal cancellation processed: reversalId={}", reversalId);
    }

    private void handleReversalTimeout(GenericKafkaEvent event, String reversalId, String transactionId, String correlationId) {
        Long timeoutDurationMinutes = (Long) event.getData().get("timeoutDurationMinutes");
        log.warn("Reversal timeout: reversalId={}, transactionId={}, duration={}min",
            reversalId, transactionId, timeoutDurationMinutes);

        // Record timeout
        reversalTrackingService.recordReversalTimeout(reversalId, timeoutDurationMinutes);

        // Send timeout alert
        fraudNotificationService.sendReversalTimeoutAlert(reversalId, transactionId, timeoutDurationMinutes, correlationId);

        // Initiate timeout handling
        reversalTrackingService.handleReversalTimeout(reversalId, timeoutDurationMinutes);

        log.info("Reversal timeout processed: reversalId={}", reversalId);
    }

    private void handleReversalRetryInitiated(GenericKafkaEvent event, String reversalId, String transactionId, String correlationId) {
        Integer retryAttempt = (Integer) event.getData().get("retryAttempt");
        log.info("Reversal retry initiated: reversalId={}, transactionId={}, attempt={}",
            reversalId, transactionId, retryAttempt);

        // Track retry attempt
        reversalTrackingService.trackRetryAttempt(reversalId, retryAttempt);

        log.info("Reversal retry tracking updated: reversalId={}, attempt={}", reversalId, retryAttempt);
    }

    private void handleReversalStatusUpdate(GenericKafkaEvent event, String reversalId, String transactionId, String correlationId) {
        String newStatus = (String) event.getData().get("status");
        String statusDetails = (String) event.getData().get("statusDetails");
        log.info("Reversal status update: reversalId={}, status={}, details={}", reversalId, newStatus, statusDetails);

        // Update tracking status
        reversalTrackingService.updateTrackingStatus(reversalId, newStatus, statusDetails);

        log.info("Reversal status updated: reversalId={}, status={}", reversalId, newStatus);
    }

    private int extractRetryNumber(String topic) {
        try {
            if (topic != null && topic.contains("-retry-")) {
                String[] parts = topic.split("-retry-");
                if (parts.length > 1) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Could not extract retry number from topic: {}", topic);
        }
        return 0;
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}