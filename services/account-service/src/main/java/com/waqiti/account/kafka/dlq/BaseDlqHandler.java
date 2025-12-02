package com.waqiti.account.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.entity.DlqRetryRecord;
import com.waqiti.account.entity.ManualReviewRecord;
import com.waqiti.account.entity.PermanentFailureRecord;
import com.waqiti.account.repository.DlqRetryRepository;
import com.waqiti.account.repository.ManualReviewRepository;
import com.waqiti.account.repository.PermanentFailureRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Base DLQ handler with intelligent message recovery framework
 *
 * <p>Provides enterprise-grade DLQ processing with:</p>
 * <ul>
 *   <li>Exponential backoff retry scheduling (5s initial, 2x multiplier)</li>
 *   <li>Intelligent error classification and routing</li>
 *   <li>Manual review queue with SLA tracking</li>
 *   <li>Permanent failure audit trail (7-year retention)</li>
 *   <li>Metrics and alerting integration</li>
 *   <li>PII sanitization for compliance</li>
 * </ul>
 *
 * <h3>Recovery Strategies:</h3>
 * <pre>
 * RETRY          → Schedule with exponential backoff (max 3 attempts)
 * MANUAL_REVIEW  → Queue for ops team (SLA-tracked)
 * PERMANENT      → Audit log (7-year retention, PII masked)
 * DISCARD        → Log and discard (use sparingly)
 * </pre>
 *
 * <h3>Implementation Guide:</h3>
 * <pre>
 * 1. Extend this class for each consumer's DLQ handler
 * 2. Implement classifyFailure() to analyze error patterns
 * 3. Implement attemptRecovery() for retry-eligible failures
 * 4. Implement maskPii() to sanitize sensitive data
 * 5. Configure alerting thresholds in getAlertThresholds()
 * </pre>
 *
 * @param <T> Message event type
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Slf4j
public abstract class BaseDlqHandler<T> {

    @Autowired
    protected DlqRetryRepository retryRepository;

    @Autowired
    protected ManualReviewRepository manualReviewRepository;

    @Autowired
    protected PermanentFailureRepository permanentFailureRepository;

    @Autowired
    protected MeterRegistry meterRegistry;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired(required = false)
    protected DlqAlertService alertService;

    @Autowired(required = false)
    protected DlqMetricsService metricsService;

    // Retry configuration
    private static final long INITIAL_BACKOFF_MS = 5000;  // 5 seconds
    private static final int BACKOFF_MULTIPLIER = 2;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // Metrics counters
    protected Counter messagesProcessed;
    protected Counter messagesRetried;
    protected Counter messagesFailedPermanently;
    protected Counter messagesRequiringReview;
    protected Counter messagesDiscarded;

    /**
     * Process DLQ message with intelligent recovery
     *
     * @param event Failed message payload
     * @param topic Original Kafka topic
     * @param partition Original partition
     * @param offset Original offset
     * @param headers Kafka headers (includes exception info)
     * @param acknowledgment Kafka acknowledgment
     */
    @Transactional
    public void processDlqMessage(
            T event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            @Header(value = "kafka_receivedMessageKey", required = false) String key,
            Map<String, Object> headers,
            Acknowledgment acknowledgment) {

        String correlationId = extractCorrelationId(headers);
        log.info("[DLQ-{}] Processing message - topic={}, partition={}, offset={}, correlationId={}",
            getHandlerName(), topic, partition, offset, correlationId);

        try {
            // Check for duplicate processing
            if (isDuplicateMessage(topic, partition, offset)) {
                log.warn("[DLQ-{}] Duplicate message detected, skipping - offset={}",
                    getHandlerName(), offset);
                incrementCounter(messagesDiscarded);
                acknowledgment.acknowledge();
                return;
            }

            // Extract failure metadata
            String exceptionMessage = extractHeader(headers, "exception-message");
            String exceptionClass = extractHeader(headers, "exception-class");
            String stackTrace = extractHeader(headers, "exception-stacktrace");
            Integer retryAttempt = extractIntHeader(headers, "retry-count", 0);

            // Classify failure to determine recovery strategy
            RecoveryDecision decision = classifyFailure(
                event, exceptionMessage, exceptionClass, retryAttempt, headers);

            log.info("[DLQ-{}] Failure classified - strategy={}, reason={}",
                getHandlerName(), decision.getStrategy(), decision.getReason());

            // Route to appropriate recovery handler
            switch (decision.getStrategy()) {
                case RETRY -> handleRetryStrategy(
                    event, topic, partition, offset, key, headers,
                    exceptionMessage, exceptionClass, stackTrace, retryAttempt, decision, correlationId);

                case MANUAL_REVIEW -> handleManualReviewStrategy(
                    event, topic, partition, offset, key, headers,
                    exceptionMessage, exceptionClass, stackTrace, retryAttempt, decision, correlationId);

                case PERMANENT_FAILURE -> handlePermanentFailureStrategy(
                    event, topic, partition, offset, key, headers,
                    exceptionMessage, exceptionClass, stackTrace, retryAttempt, decision, correlationId);

                case DISCARD -> handleDiscardStrategy(
                    event, topic, partition, offset, decision, correlationId);
            }

            // Acknowledge after successful processing
            acknowledgment.acknowledge();

            // Update metrics
            incrementCounter(messagesProcessed);

        } catch (Exception e) {
            log.error("[DLQ-{}] Critical error in DLQ handler itself - offset={}",
                getHandlerName(), offset, e);

            // Handler failure is critical - alert and do NOT acknowledge
            if (alertService != null) {
                alertService.sendCriticalAlert(
                    getHandlerName() + " DLQ Handler Failure",
                    "DLQ handler itself failed: " + e.getMessage(),
                    Map.of("topic", topic, "offset", offset.toString()));
            }

            // DO NOT acknowledge - message will be reprocessed
            throw new DlqHandlerException("DLQ handler failure", e);
        }
    }

    /**
     * Handle RETRY strategy - schedule with exponential backoff
     */
    private void handleRetryStrategy(
            T event, String topic, Integer partition, Long offset, String key,
            Map<String, Object> headers, String exceptionMessage, String exceptionClass,
            String stackTrace, Integer retryAttempt, RecoveryDecision decision, String correlationId) {

        // Check if already at max retries
        if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
            log.warn("[DLQ-{}] Max retries exceeded ({}), escalating to manual review",
                getHandlerName(), MAX_RETRY_ATTEMPTS);

            // Escalate to manual review
            handleManualReviewStrategy(
                event, topic, partition, offset, key, headers,
                exceptionMessage, exceptionClass, stackTrace, retryAttempt,
                RecoveryDecision.manualReview(
                    ManualReviewRecord.ReviewPriority.HIGH,
                    "Max retries exceeded: " + decision.getReason()),
                correlationId);
            return;
        }

        // Calculate next retry time with exponential backoff
        long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(BACKOFF_MULTIPLIER, retryAttempt);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(backoffMs * 1_000_000);

        // Sanitize payload (mask PII)
        String sanitizedPayload = maskPii(serializeEvent(event));

        // Create retry record
        DlqRetryRecord retryRecord = DlqRetryRecord.builder()
            .originalTopic(topic)
            .originalPartition(partition)
            .originalOffset(offset)
            .originalKey(key)
            .payload(sanitizedPayload)
            .exceptionMessage(exceptionMessage)
            .exceptionClass(exceptionClass)
            .exceptionStackTrace(stackTrace)
            .failedAt(extractTimestamp(headers))
            .failureReason(decision.getReason())
            .retryAttempt(retryAttempt)
            .maxRetryAttempts(MAX_RETRY_ATTEMPTS)
            .nextRetryAt(nextRetryAt)
            .retryReason(decision.getReason())
            .backoffDelayMs(backoffMs)
            .status(DlqRetryRecord.RetryStatus.PENDING)
            .handlerName(getHandlerName())
            .recoveryAction(decision.getRecoveryAction())
            .correlationId(correlationId)
            .build();

        retryRepository.save(retryRecord);

        log.info("[DLQ-{}] Scheduled retry - attempt={}/{}, nextRetry={}, backoffMs={}",
            getHandlerName(), retryAttempt + 1, MAX_RETRY_ATTEMPTS, nextRetryAt, backoffMs);

        incrementCounter(messagesRetried);

        // Alert if approaching max retries
        if (retryAttempt >= MAX_RETRY_ATTEMPTS - 1 && alertService != null) {
            alertService.sendWarningAlert(
                getHandlerName() + " - Final Retry Attempt",
                "Message on final retry attempt before manual review",
                Map.of("topic", topic, "offset", offset.toString(), "correlationId", correlationId));
        }
    }

    /**
     * Handle MANUAL_REVIEW strategy - queue for ops team
     */
    private void handleManualReviewStrategy(
            T event, String topic, Integer partition, Long offset, String key,
            Map<String, Object> headers, String exceptionMessage, String exceptionClass,
            String stackTrace, Integer retryAttempt, RecoveryDecision decision, String correlationId) {

        String sanitizedPayload = maskPii(serializeEvent(event));

        ManualReviewRecord reviewRecord = ManualReviewRecord.builder()
            .originalTopic(topic)
            .originalPartition(partition)
            .originalOffset(offset)
            .originalKey(key)
            .payload(sanitizedPayload)
            .exceptionMessage(exceptionMessage)
            .exceptionClass(exceptionClass)
            .exceptionStackTrace(stackTrace)
            .failedAt(extractTimestamp(headers))
            .retryAttempts(retryAttempt)
            .reviewReason(decision.getReason())
            .priority(decision.getPriority())
            .status(ManualReviewRecord.ReviewStatus.PENDING)
            .handlerName(getHandlerName())
            .correlationId(correlationId)
            .contextData(buildContextData(event, headers))
            .build();

        manualReviewRepository.save(reviewRecord);

        log.warn("[DLQ-{}] Queued for manual review - priority={}, reason={}",
            getHandlerName(), decision.getPriority(), decision.getReason());

        incrementCounter(messagesRequiringReview);

        // Alert based on priority
        if (alertService != null) {
            if (decision.getPriority() == ManualReviewRecord.ReviewPriority.CRITICAL) {
                alertService.sendCriticalAlert(
                    getHandlerName() + " - CRITICAL Manual Review Required",
                    decision.getReason(),
                    Map.of("topic", topic, "offset", offset.toString(),
                           "correlationId", correlationId, "sla", "15 minutes"));
            } else if (decision.getPriority() == ManualReviewRecord.ReviewPriority.HIGH) {
                alertService.sendHighPriorityAlert(
                    getHandlerName() + " - Manual Review Required",
                    decision.getReason(),
                    Map.of("topic", topic, "offset", offset.toString(),
                           "correlationId", correlationId, "sla", "1 hour"));
            }
        }
    }

    /**
     * Handle PERMANENT_FAILURE strategy - audit log with 7-year retention
     */
    private void handlePermanentFailureStrategy(
            T event, String topic, Integer partition, Long offset, String key,
            Map<String, Object> headers, String exceptionMessage, String exceptionClass,
            String stackTrace, Integer retryAttempt, RecoveryDecision decision, String correlationId) {

        String sanitizedPayload = maskPii(serializeEvent(event));

        PermanentFailureRecord failureRecord = PermanentFailureRecord.builder()
            .originalTopic(topic)
            .originalPartition(partition)
            .originalOffset(offset)
            .originalKey(key)
            .payload(sanitizedPayload)
            .failureReason(decision.getReason())
            .failureCategory(decision.getFailureCategory())
            .exceptionMessage(exceptionMessage)
            .exceptionClass(exceptionClass)
            .exceptionStackTrace(stackTrace)
            .failedAt(extractTimestamp(headers))
            .retryAttempts(retryAttempt)
            .handlerName(getHandlerName())
            .correlationId(correlationId)
            .traceId(extractHeader(headers, "trace-id"))
            .contextData(buildContextData(event, headers))
            .businessImpact(decision.getBusinessImpact())
            .impactDescription(decision.getImpactDescription())
            .financialImpactAmount(decision.getFinancialImpact())
            .build();

        permanentFailureRepository.save(failureRecord);

        log.error("[DLQ-{}] Permanent failure recorded - category={}, impact={}, reason={}",
            getHandlerName(), decision.getFailureCategory(),
            decision.getBusinessImpact(), decision.getReason());

        incrementCounter(messagesFailedPermanently);

        // Alert for high/critical impact failures
        if (alertService != null &&
            (decision.getBusinessImpact() == PermanentFailureRecord.BusinessImpact.HIGH ||
             decision.getBusinessImpact() == PermanentFailureRecord.BusinessImpact.CRITICAL)) {

            alertService.sendCriticalAlert(
                getHandlerName() + " - Permanent Failure (" + decision.getBusinessImpact() + ")",
                decision.getReason(),
                Map.of("topic", topic, "offset", offset.toString(),
                       "category", decision.getFailureCategory().toString(),
                       "correlationId", correlationId));
        }
    }

    /**
     * Handle DISCARD strategy - log and discard
     */
    private void handleDiscardStrategy(
            T event, String topic, Integer partition, Long offset,
            RecoveryDecision decision, String correlationId) {

        log.warn("[DLQ-{}] Message discarded - reason={}, topic={}, offset={}",
            getHandlerName(), decision.getReason(), topic, offset);

        incrementCounter(messagesDiscarded);

        // Still track discarded messages for audit
        if (metricsService != null) {
            metricsService.recordDiscardedMessage(
                getHandlerName(), topic, decision.getReason());
        }
    }

    /**
     * Check if message was already processed (idempotency)
     */
    private boolean isDuplicateMessage(String topic, Integer partition, Long offset) {
        // Check retry queue
        if (retryRepository.findByOriginalTopicAndOriginalPartitionAndOriginalOffset(
            topic, partition, offset).isPresent()) {
            return true;
        }

        // Check manual review queue
        if (manualReviewRepository.findByOriginalTopicAndOriginalPartitionAndOriginalOffset(
            topic, partition, offset).isPresent()) {
            return true;
        }

        // Check permanent failures
        return permanentFailureRepository.findByOriginalTopicAndOriginalPartitionAndOriginalOffset(
            topic, partition, offset).isPresent();
    }

    /**
     * Serialize event to JSON string
     */
    private String serializeEvent(T event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
            return event.toString();
        }
    }

    /**
     * Build context data map for additional metadata
     */
    private Map<String, Object> buildContextData(T event, Map<String, Object> headers) {
        Map<String, Object> context = new HashMap<>();
        context.put("handler", getHandlerName());
        context.put("timestamp", LocalDateTime.now().toString());
        context.put("eventType", event.getClass().getSimpleName());

        // Add relevant headers
        Optional.ofNullable(headers.get("trace-id"))
            .ifPresent(v -> context.put("traceId", v.toString()));
        Optional.ofNullable(headers.get("span-id"))
            .ifPresent(v -> context.put("spanId", v.toString()));

        return context;
    }

    /**
     * Extract correlation ID from headers or generate new one
     */
    private String extractCorrelationId(Map<String, Object> headers) {
        return Optional.ofNullable(extractHeader(headers, "correlation-id"))
            .orElse(UUID.randomUUID().toString());
    }

    /**
     * Extract timestamp from headers or use current time
     */
    private LocalDateTime extractTimestamp(Map<String, Object> headers) {
        return Optional.ofNullable(extractHeader(headers, "timestamp"))
            .map(ts -> LocalDateTime.parse(ts))
            .orElse(LocalDateTime.now());
    }

    /**
     * Extract string header value
     */
    protected String extractHeader(Map<String, Object> headers, String key) {
        Object value = headers.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Extract integer header value
     */
    protected Integer extractIntHeader(Map<String, Object> headers, String key, Integer defaultValue) {
        Object value = headers.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Increment metrics counter safely
     */
    protected void incrementCounter(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * Initialize metrics counters (call from @PostConstruct)
     */
    protected void initializeMetrics() {
        String handlerName = getHandlerName();

        messagesProcessed = Counter.builder("dlq.messages.processed")
            .tag("handler", handlerName)
            .description("Total DLQ messages processed")
            .register(meterRegistry);

        messagesRetried = Counter.builder("dlq.messages.retried")
            .tag("handler", handlerName)
            .description("Messages scheduled for retry")
            .register(meterRegistry);

        messagesFailedPermanently = Counter.builder("dlq.messages.permanent_failure")
            .tag("handler", handlerName)
            .description("Messages marked as permanent failures")
            .register(meterRegistry);

        messagesRequiringReview = Counter.builder("dlq.messages.manual_review")
            .tag("handler", handlerName)
            .description("Messages requiring manual review")
            .register(meterRegistry);

        messagesDiscarded = Counter.builder("dlq.messages.discarded")
            .tag("handler", handlerName)
            .description("Messages discarded")
            .register(meterRegistry);
    }

    // ========== ABSTRACT METHODS (MUST BE IMPLEMENTED BY SUBCLASSES) ==========

    /**
     * Classify failure and determine recovery strategy
     *
     * <p>Analyze exception message, class, retry count, and event payload
     * to determine the appropriate recovery strategy.</p>
     *
     * @param event Failed event
     * @param exceptionMessage Exception message from failure
     * @param exceptionClass Exception class name
     * @param retryAttempt Current retry attempt count
     * @param headers Kafka headers
     * @return Recovery decision with strategy and metadata
     */
    protected abstract RecoveryDecision classifyFailure(
        T event,
        String exceptionMessage,
        String exceptionClass,
        Integer retryAttempt,
        Map<String, Object> headers);

    /**
     * Attempt to recover message (for RETRY strategy)
     *
     * <p>Implement custom recovery logic here. This method is called
     * by the scheduled retry processor.</p>
     *
     * @param retryRecord Retry record from database
     * @return true if recovery successful, false otherwise
     */
    protected abstract boolean attemptRecovery(DlqRetryRecord retryRecord);

    /**
     * Mask PII in message payload for compliance
     *
     * <p>CRITICAL: All sensitive data (SSN, card numbers, passwords, etc.)
     * MUST be masked before storing in DLQ tables.</p>
     *
     * @param payload Serialized event JSON
     * @return Sanitized payload with PII masked
     */
    protected abstract String maskPii(String payload);

    /**
     * Get handler name for metrics and logging
     *
     * @return Handler name (e.g., "AccountCreatedEventsConsumer")
     */
    protected abstract String getHandlerName();

    // ========== EXCEPTION CLASSES ==========

    /**
     * Exception indicating DLQ handler itself failed
     */
    public static class DlqHandlerException extends RuntimeException {
        public DlqHandlerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
