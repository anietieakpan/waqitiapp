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
 * Production-grade Kafka consumer for async reversal pending events
 * Handles pending asynchronous transaction reversals for fraud mitigation
 *
 * Critical for: Transaction reversal processing, fraud mitigation, financial integrity
 * SLA: Must process pending reversals within 30 seconds for timely resolution
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncReversalPendingConsumer {

    private final AsyncReversalService asyncReversalService;
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
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("async_reversal_pending_processed_total")
            .description("Total number of successfully processed async reversal pending events")
            .register(meterRegistry);
        errorCounter = Counter.builder("async_reversal_pending_errors_total")
            .description("Total number of async reversal pending processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("async_reversal_pending_processing_duration")
            .description("Time taken to process async reversal pending events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"async-reversal-pending", "fraud-reversals-pending"},
        groupId = "fraud-async-reversal-pending-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "async-reversal-pending", fallbackMethod = "handleAsyncReversalPendingEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAsyncReversalPendingEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("async-rev-pending-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing async reversal pending: id={}, type={}, transactionId={}",
                event.getId(), event.getEventType(), event.getData().get("transactionId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String transactionId = (String) event.getData().get("transactionId");
            String userId = (String) event.getData().get("userId");
            String reversalId = (String) event.getData().get("reversalId");

            switch (event.getEventType()) {
                case "REVERSAL_PENDING":
                    handleReversalPending(event, transactionId, userId, reversalId, correlationId);
                    break;

                case "REVERSAL_QUEUED":
                    handleReversalQueued(event, transactionId, reversalId, correlationId);
                    break;

                case "REVERSAL_PROCESSING":
                    handleReversalProcessing(event, transactionId, reversalId, correlationId);
                    break;

                case "REVERSAL_TIMEOUT_WARNING":
                    handleReversalTimeoutWarning(event, transactionId, reversalId, correlationId);
                    break;

                case "REVERSAL_ESCALATION_REQUIRED":
                    handleReversalEscalationRequired(event, transactionId, reversalId, correlationId);
                    break;

                case "REVERSAL_BLOCKED":
                    handleReversalBlocked(event, transactionId, reversalId, correlationId);
                    break;

                case "REVERSAL_RETRY_SCHEDULED":
                    handleReversalRetryScheduled(event, transactionId, reversalId, correlationId);
                    break;

                default:
                    log.warn("Unknown async reversal pending event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFinancialEvent("ASYNC_REVERSAL_PENDING_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "transactionId", transactionId,
                    "reversalId", reversalId, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process async reversal pending event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("async-reversal-pending-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAsyncReversalPendingEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("async-rev-pending-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for async reversal pending: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("async-reversal-pending-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Async Reversal Pending Consumer Circuit Breaker Triggered",
                String.format("Async reversal pending event %s failed: %s", event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAsyncReversalPendingEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-async-rev-pending-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Async reversal pending permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logFinancialEvent("ASYNC_REVERSAL_PENDING_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Async Reversal Pending Event Dead Letter",
                String.format("Async reversal pending event %s sent to DLT: %s", event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void handleReversalPending(GenericKafkaEvent event, String transactionId, String userId, String reversalId, String correlationId) {
        BigDecimal amount = new BigDecimal(event.getData().get("amount").toString());
        String reason = (String) event.getData().get("reason");
        log.info("Reversal pending: transactionId={}, reversalId={}, amount={}, reason={}",
            transactionId, reversalId, amount, reason);

        // Process reversal request
        asyncReversalService.processReversalRequest(reversalId, transactionId, userId, amount, reason);

        // Validate reversal eligibility
        boolean eligible = transactionReversalService.validateReversalEligibility(transactionId, amount);

        if (!eligible) {
            log.warn("Reversal not eligible: transactionId={}, reversalId={}", transactionId, reversalId);
            handleIneligibleReversal(reversalId, transactionId, correlationId);
            return;
        }

        // Queue for processing
        asyncReversalService.queueReversalForProcessing(reversalId, transactionId);

        // Send confirmation to customer
        customerCommunicationService.sendReversalPendingNotification(userId, transactionId, reversalId);

        // Send to reversal tracking
        kafkaTemplate.send("async-reversal-tracking", Map.of(
            "transactionId", transactionId,
            "reversalId", reversalId,
            "userId", userId,
            "eventType", "REVERSAL_QUEUED_FOR_PROCESSING",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Reversal pending processed: reversalId={}", reversalId);
    }

    private void handleReversalQueued(GenericKafkaEvent event, String transactionId, String reversalId, String correlationId) {
        Integer queuePosition = (Integer) event.getData().get("queuePosition");
        log.info("Reversal queued: transactionId={}, reversalId={}, position={}", transactionId, reversalId, queuePosition);

        // Update reversal status
        asyncReversalService.updateReversalStatus(reversalId, "QUEUED", queuePosition);

        // Estimate processing time
        Long estimatedProcessingTime = asyncReversalService.estimateProcessingTime(queuePosition);

        // Send queue position notification
        String userId = (String) event.getData().get("userId");
        customerCommunicationService.sendQueuePositionUpdate(userId, reversalId, queuePosition, estimatedProcessingTime);

        log.info("Reversal queued processed: reversalId={}, position={}", reversalId, queuePosition);
    }

    private void handleReversalProcessing(GenericKafkaEvent event, String transactionId, String reversalId, String correlationId) {
        String processingStage = (String) event.getData().get("processingStage");
        log.info("Reversal processing: transactionId={}, reversalId={}, stage={}", transactionId, reversalId, processingStage);

        // Update reversal status
        asyncReversalService.updateReversalStatus(reversalId, "PROCESSING", processingStage);

        // Monitor processing progress
        asyncReversalService.monitorProcessingProgress(reversalId, processingStage);

        // Send processing notification
        String userId = (String) event.getData().get("userId");
        customerCommunicationService.sendReversalProcessingUpdate(userId, reversalId, processingStage);

        log.info("Reversal processing updated: reversalId={}, stage={}", reversalId, processingStage);
    }

    private void handleReversalTimeoutWarning(GenericKafkaEvent event, String transactionId, String reversalId, String correlationId) {
        Long processingTimeMinutes = (Long) event.getData().get("processingTimeMinutes");
        log.warn("Reversal timeout warning: transactionId={}, reversalId={}, processingTime={}min",
            transactionId, reversalId, processingTimeMinutes);

        // Check reversal status
        asyncReversalService.checkReversalTimeout(reversalId, processingTimeMinutes);

        // Send timeout warning to operations
        fraudNotificationService.sendReversalTimeoutWarning(reversalId, transactionId, processingTimeMinutes, correlationId);

        // Consider escalation
        if (processingTimeMinutes > 60) {
            asyncReversalService.considerEscalation(reversalId, "TIMEOUT_EXCEEDED");
        }

        log.info("Reversal timeout warning processed: reversalId={}", reversalId);
    }

    private void handleReversalEscalationRequired(GenericKafkaEvent event, String transactionId, String reversalId, String correlationId) {
        String escalationReason = (String) event.getData().get("escalationReason");
        log.warn("Reversal escalation required: transactionId={}, reversalId={}, reason={}",
            transactionId, reversalId, escalationReason);

        // Escalate reversal
        asyncReversalService.escalateReversal(reversalId, escalationReason);

        // Notify senior operations team
        fraudNotificationService.sendReversalEscalationAlert(reversalId, transactionId, escalationReason, correlationId);

        // Update priority
        asyncReversalService.updateReversalPriority(reversalId, "HIGH");

        log.info("Reversal escalation processed: reversalId={}", reversalId);
    }

    private void handleReversalBlocked(GenericKafkaEvent event, String transactionId, String reversalId, String correlationId) {
        String blockReason = (String) event.getData().get("blockReason");
        log.warn("Reversal blocked: transactionId={}, reversalId={}, reason={}", transactionId, reversalId, blockReason);

        // Process reversal blocking
        asyncReversalService.blockReversal(reversalId, blockReason);

        // Notify customer of blocking
        String userId = (String) event.getData().get("userId");
        customerCommunicationService.sendReversalBlockedNotification(userId, reversalId, blockReason);

        // Send to manual review queue
        kafkaTemplate.send("manual-review-queue", Map.of(
            "transactionId", transactionId,
            "reversalId", reversalId,
            "userId", userId,
            "reviewType", "BLOCKED_REVERSAL",
            "blockReason", blockReason,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Reversal blocking processed: reversalId={}", reversalId);
    }

    private void handleReversalRetryScheduled(GenericKafkaEvent event, String transactionId, String reversalId, String correlationId) {
        Long retryDelayMinutes = (Long) event.getData().get("retryDelayMinutes");
        Integer retryAttempt = (Integer) event.getData().get("retryAttempt");
        log.info("Reversal retry scheduled: transactionId={}, reversalId={}, delay={}min, attempt={}",
            transactionId, reversalId, retryDelayMinutes, retryAttempt);

        // Schedule reversal retry
        asyncReversalService.scheduleReversalRetry(reversalId, retryDelayMinutes, retryAttempt);

        // Update reversal status
        asyncReversalService.updateReversalStatus(reversalId, "RETRY_SCHEDULED", retryAttempt);

        // Notify customer of retry
        String userId = (String) event.getData().get("userId");
        customerCommunicationService.sendReversalRetryNotification(userId, reversalId, retryDelayMinutes);

        log.info("Reversal retry scheduled: reversalId={}, attempt={}", reversalId, retryAttempt);
    }

    private void handleIneligibleReversal(String reversalId, String transactionId, String correlationId) {
        log.warn("Handling ineligible reversal: reversalId={}, transactionId={}", reversalId, transactionId);

        // Update reversal status
        asyncReversalService.updateReversalStatus(reversalId, "INELIGIBLE", "NOT_REVERSIBLE");

        // Send to manual review for evaluation
        kafkaTemplate.send("manual-review-queue", Map.of(
            "reversalId", reversalId,
            "transactionId", transactionId,
            "reviewType", "INELIGIBLE_REVERSAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
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