package com.waqiti.common.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all DLQ consumers providing common functionality.
 * Supports both simple and comprehensive DLQ patterns.
 *
 * Usage patterns:
 * 1. Simple pattern: Use the old constructor with DlqHandler for basic DLQ handling
 * 2. Comprehensive pattern: Use minimal constructor for full control over recovery logic
 */
@Slf4j
public abstract class BaseDlqConsumer {

    protected final DlqHandler dlqHandler;
    protected final AuditService auditService;
    protected final NotificationService notificationService;
    protected final MeterRegistry meterRegistry;
    protected final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    protected final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    protected static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    protected Counter successCounter;
    protected Counter errorCounter;
    protected Timer processingTimer;

    /**
     * Full constructor for simple DLQ pattern (backward compatible)
     */
    public BaseDlqConsumer(DlqHandler dlqHandler,
                          AuditService auditService,
                          NotificationService notificationService,
                          MeterRegistry meterRegistry) {
        this.dlqHandler = dlqHandler;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Minimal constructor for comprehensive DLQ pattern
     * Use when you want full control over recovery logic without DlqHandler
     */
    public BaseDlqConsumer(MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.dlqHandler = null;
        this.auditService = null;
        this.notificationService = null;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Note: Subclasses should inject IdempotencyService for distributed idempotency.
     * The in-memory processedEvents cache is kept for backward compatibility and
     * as a fast fallback, but production implementations should use IdempotencyService.
     */

    @PostConstruct
    public void initMetrics() {
        String consumerName = getConsumerName();
        successCounter = Counter.builder(consumerName + "_dlq_processed_total")
            .description("Total number of successfully processed " + consumerName + " DLQ messages")
            .register(meterRegistry);
        errorCounter = Counter.builder(consumerName + "_dlq_errors_total")
            .description("Total number of " + consumerName + " DLQ processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder(consumerName + "_dlq_processing_duration")
            .description("Time taken to process " + consumerName + " DLQ messages")
            .register(meterRegistry);
    }

    /**
     * Generic DLQ message handler that all concrete consumers should use.
     */
    protected void handleDlqMessage(@Payload Object originalMessage,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   Acknowledgment acknowledgment,
                                   Map<String, Object> headers) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String messageId = generateMessageId(topic, partition, offset);
        String eventKey = generateEventKey(originalMessage, topic, partition, offset);

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing DLQ message: topic={}, messageId={}, error={}",
                topic, messageId, exceptionMessage);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Use the generic DLQ handler
            dlqHandler.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, headers);

            // Perform domain-specific processing
            processDomainSpecificLogic(originalMessage, topic, exceptionMessage, messageId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            // Audit the processing
            auditDlqProcessing(originalMessage, topic, messageId, exceptionMessage);

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process DLQ message: topic={}, messageId={}, error={}",
                topic, messageId, e.getMessage(), e);

            // Send notification for DLQ processing failure
            sendProcessingFailureNotification(topic, messageId, e.getMessage());

            acknowledgment.acknowledge();
            throw e; // Re-throw for circuit breaker
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Domain-specific processing logic that concrete consumers must implement.
     */
    protected abstract void processDomainSpecificLogic(Object originalMessage, String topic,
                                                      String exceptionMessage, String messageId);

    /**
     * Returns the consumer name for metrics and logging.
     */
    protected abstract String getConsumerName();

    /**
     * Returns the business domain for this consumer (e.g., "FINANCIAL", "COMPLIANCE").
     */
    protected abstract String getBusinessDomain();

    /**
     * Determines if this DLQ message has critical business impact.
     */
    protected abstract boolean isCriticalBusinessImpact(Object originalMessage, String topic);

    /**
     * Generates escalation-specific notifications.
     */
    protected abstract void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                                        String exceptionMessage, String messageId);

    protected boolean isEventProcessed(String eventKey) {
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

    protected void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    protected void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    protected String generateMessageId(String topic, int partition, long offset) {
        return String.format("dlq-%s-p%d-o%d-%d", topic, partition, offset, System.currentTimeMillis());
    }

    protected String generateEventKey(Object originalMessage, String topic, int partition, long offset) {
        return String.format("%s-%s-p%d-o%d", topic, originalMessage.hashCode(), partition, offset);
    }

    /**
     * Generate event ID from ConsumerRecord (for comprehensive pattern)
     */
    protected String generateEventId(ConsumerRecord<String, String> record, String topic, int partition, long offset) {
        return String.format("%s-p%d-o%d-%s", topic, partition, offset, record.key());
    }

    /**
     * Generate correlation ID for tracking
     */
    protected String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Check if event already processed (by event ID)
     */
    protected boolean isAlreadyProcessed(String eventId) {
        Long timestamp = processedEvents.get(eventId);
        if (timestamp == null) {
            return false;
        }

        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventId);
            return false;
        }

        return true;
    }

    /**
     * Mark event as processed (by event ID)
     */
    protected void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanExpiredEntries();
        }
    }

    /**
     * Handle validation failures - can be overridden by subclasses
     */
    protected void handleValidationFailure(ConsumerRecord<String, String> record, Exception e, String correlationId) {
        log.error("Validation failure in {}: correlationId={}, error={}",
            getConsumerName(), correlationId, e.getMessage());
    }

    /**
     * Handle critical failures - can be overridden by subclasses
     */
    protected void handleCriticalFailure(ConsumerRecord<String, String> record, Exception e, String correlationId) {
        log.error("Critical failure in {}: correlationId={}", getConsumerName(), correlationId, e);
    }

    protected void auditDlqProcessing(Object originalMessage, String topic, String messageId, String exceptionMessage) {
        auditService.logDlqEvent("DLQ_MESSAGE_PROCESSED", topic,
            Map.of("messageId", messageId,
                   "consumerName", getConsumerName(),
                   "businessDomain", getBusinessDomain(),
                   "errorMessage", exceptionMessage,
                   "originalMessageType", originalMessage.getClass().getSimpleName(),
                   "processingTimestamp", Instant.now()));
    }

    protected void sendProcessingFailureNotification(String topic, String messageId, String error) {
        try {
            com.waqiti.common.notification.model.CriticalAlertRequest request =
                com.waqiti.common.notification.model.CriticalAlertRequest.builder()
                    .title("DLQ Consumer Processing Failure")
                    .message(String.format("Failed to process DLQ message in %s consumer: topic=%s, messageId=%s, error=%s",
                        getConsumerName(), topic, messageId, error))
                    .severity(com.waqiti.common.notification.model.CriticalAlertRequest.AlertSeverity.CRITICAL)
                    .source(getConsumerName())
                    .metadata(Map.of("consumerName", getConsumerName(),
                           "businessDomain", getBusinessDomain(),
                           "topic", topic,
                           "messageId", messageId))
                    .build();
            notificationService.sendCriticalAlert(request);
        } catch (Exception e) {
            log.error("Failed to send DLQ processing failure notification: {}", e.getMessage());
        }
    }

    /**
     * Helper method to extract correlation ID from message headers or content.
     */
    protected String extractCorrelationId(Map<String, Object> headers, Object originalMessage) {
        if (headers != null && headers.containsKey("correlationId")) {
            return headers.get("correlationId").toString();
        }

        // Try to extract from message content if it's a common event type
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                if (messageMap.containsKey("correlationId")) {
                    return messageMap.get("correlationId").toString();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract correlation ID from message content: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Helper method to determine if a message should trigger immediate escalation.
     */
    protected boolean shouldEscalateImmediately(Object originalMessage, String topic, String exceptionMessage) {
        // Check for security-related topics
        if (topic.toLowerCase().contains("security") || topic.toLowerCase().contains("fraud") ||
            topic.toLowerCase().contains("auth")) {
            return true;
        }

        // Check for financial impact
        if (isCriticalBusinessImpact(originalMessage, topic)) {
            return true;
        }

        // Check for repeated failures
        String errorType = exceptionMessage.toLowerCase();
        if (errorType.contains("database") && errorType.contains("connection")) {
            return true;
        }

        return false;
    }

    /**
     * Standard fallback method for circuit breaker integration.
     */
    protected void handleDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                    int partition, long offset, Acknowledgment acknowledgment, Exception ex) {

        String messageId = generateMessageId(topic, partition, offset);

        log.error("Circuit breaker fallback triggered for DLQ consumer {}: topic={}, messageId={}, error={}",
            getConsumerName(), topic, messageId, ex.getMessage());

        try {
            // Send to permanent failure queue
            Map<String, Object> failureData = Map.of(
                "originalMessage", originalMessage,
                "topic", topic,
                "error", ex.getMessage(),
                "errorType", "CIRCUIT_BREAKER_FALLBACK",
                "consumerName", getConsumerName(),
                "businessDomain", getBusinessDomain(),
                "messageId", messageId,
                "timestamp", Instant.now()
            );

            // This would typically be sent to a monitoring system or permanent storage
            auditService.logDlqEvent("DLQ_CIRCUIT_BREAKER_FALLBACK", topic, failureData);

            // Send critical notification
            com.waqiti.common.notification.model.CriticalAlertRequest alertRequest =
                com.waqiti.common.notification.model.CriticalAlertRequest.builder()
                    .title("DLQ Consumer Circuit Breaker Triggered")
                    .message(String.format("Circuit breaker triggered for %s DLQ consumer: %s",
                        getConsumerName(), ex.getMessage()))
                    .severity(com.waqiti.common.notification.model.CriticalAlertRequest.AlertSeverity.EMERGENCY)
                    .source(getConsumerName())
                    .metadata(Map.of("consumerName", getConsumerName(),
                           "topic", topic,
                           "messageId", messageId))
                    .build();
            notificationService.sendCriticalAlert(alertRequest);

        } catch (Exception notificationEx) {
            log.error("Failed to handle circuit breaker fallback: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }
}