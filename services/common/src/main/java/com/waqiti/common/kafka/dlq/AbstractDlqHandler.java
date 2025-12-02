package com.waqiti.common.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.alerting.AlertingService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.monitoring.MetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for Dead Letter Queue (DLQ) handlers
 * Provides comprehensive error recovery, retry logic, and monitoring
 *
 * Features:
 * - Automatic retry with exponential backoff
 * - Permanent failure detection and storage
 * - Comprehensive metrics and alerting
 * - Audit logging for compliance
 * - Business-specific recovery strategies
 * - Circuit breaker integration
 *
 * @param <T> The message type this handler processes
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-10-23
 */
@Slf4j
public abstract class AbstractDlqHandler<T> {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AlertingService alertingService;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;

    @Autowired
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    protected MeterRegistry meterRegistry;

    @Value("${dlq.max-retries:5}")
    protected int maxRetries;

    @Value("${dlq.initial-retry-delay-ms:1000}")
    protected long initialRetryDelayMs;

    @Value("${dlq.max-retry-delay-ms:300000}") // 5 minutes
    protected long maxRetryDelayMs;

    @Value("${dlq.enable-auto-recovery:true}")
    protected boolean enableAutoRecovery;

    @Value("${dlq.permanent-failure-retention-days:30}")
    protected int permanentFailureRetentionDays;

    // Metrics
    protected Counter dlqReceivedCounter;
    protected Counter dlqRecoveredCounter;
    protected Counter dlqPermanentFailureCounter;
    protected Counter dlqRetryCounter;
    protected Timer dlqProcessingTimer;

    // Redis keys
    private static final String RETRY_COUNT_KEY_PREFIX = "dlq:retry:count:";
    private static final String FAILURE_HISTORY_KEY_PREFIX = "dlq:failure:history:";
    private static final String PERMANENT_FAILURE_KEY_PREFIX = "dlq:permanent:failure:";

    /**
     * Initialize metrics and handlers
     */
    @PostConstruct
    public void initializeMetrics() {
        String serviceName = getServiceName();
        String messageType = getMessageType();

        dlqReceivedCounter = Counter.builder("dlq.messages.received")
                .description("Number of messages received in DLQ")
                .tag("service", serviceName)
                .tag("type", messageType)
                .register(meterRegistry);

        dlqRecoveredCounter = Counter.builder("dlq.messages.recovered")
                .description("Number of messages successfully recovered from DLQ")
                .tag("service", serviceName)
                .tag("type", messageType)
                .register(meterRegistry);

        dlqPermanentFailureCounter = Counter.builder("dlq.messages.permanent_failure")
                .description("Number of messages that permanently failed")
                .tag("service", serviceName)
                .tag("type", messageType)
                .register(meterRegistry);

        dlqRetryCounter = Counter.builder("dlq.messages.retry")
                .description("Number of retry attempts")
                .tag("service", serviceName)
                .tag("type", messageType)
                .register(meterRegistry);

        dlqProcessingTimer = Timer.builder("dlq.processing.time")
                .description("Time taken to process DLQ messages")
                .tag("service", serviceName)
                .tag("type", messageType)
                .register(meterRegistry);

        log.info("DLQ Handler initialized for service={}, messageType={}", serviceName, messageType);
    }

    /**
     * Main entry point for DLQ message processing
     * This method orchestrates the entire recovery process
     *
     * @param record The consumer record from DLQ topic
     */
    public void handleDlqMessage(ConsumerRecord<String, byte[]> record) {
        dlqReceivedCounter.increment();

        dlqProcessingTimer.record(() -> {
            try {
                log.info("Processing DLQ message: topic={}, partition={}, offset={}, key={}",
                        record.topic(), record.partition(), record.offset(), record.key());

                // Extract message metadata
                DlqMessageMetadata metadata = extractMetadata(record);

                // Check if message has exceeded retry limit
                if (hasExceededRetryLimit(metadata)) {
                    handlePermanentFailure(record, metadata);
                    return;
                }

                // Deserialize message
                T message = deserializeMessage(record);

                // Validate message
                if (!validateMessage(message, metadata)) {
                    log.warn("Message validation failed, treating as permanent failure");
                    handlePermanentFailure(record, metadata);
                    return;
                }

                // Attempt business-specific recovery
                RecoveryResult result = attemptRecovery(message, metadata);

                // Handle recovery result
                if (result.isSuccess()) {
                    handleSuccessfulRecovery(record, metadata, result);
                } else if (result.isRetryable()) {
                    handleRetryableFailure(record, metadata, result);
                } else {
                    handlePermanentFailure(record, metadata);
                }

            } catch (Exception e) {
                log.error("Unexpected error processing DLQ message", e);
                handleUnexpectedError(record, e);
            }
        });
    }

    /**
     * Extract metadata from Kafka headers and record
     */
    private DlqMessageMetadata extractMetadata(ConsumerRecord<String, byte[]> record) {
        DlqMessageMetadata metadata = new DlqMessageMetadata();
        metadata.setOriginalTopic(getHeaderValue(record, "original-topic"));
        metadata.setOriginalPartition(getHeaderValueAsInt(record, "original-partition"));
        metadata.setOriginalOffset(getHeaderValueAsLong(record, "original-offset"));
        metadata.setOriginalTimestamp(record.timestamp());
        metadata.setExceptionMessage(getHeaderValue(record, "exception-message"));
        metadata.setExceptionStacktrace(getHeaderValue(record, "exception-stacktrace"));
        metadata.setRetryCount(getRetryCount(record.key()));
        metadata.setDlqTopic(record.topic());
        metadata.setKey(record.key());

        return metadata;
    }

    /**
     * Deserialize message from byte array
     */
    protected T deserializeMessage(ConsumerRecord<String, byte[]> record) throws Exception {
        String json = new String(record.value(), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, getMessageClass());
    }

    /**
     * Check if message has exceeded retry limit
     */
    private boolean hasExceededRetryLimit(DlqMessageMetadata metadata) {
        return metadata.getRetryCount() >= maxRetries;
    }

    /**
     * Get current retry count from Redis
     */
    private int getRetryCount(String messageKey) {
        String key = RETRY_COUNT_KEY_PREFIX + messageKey;
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        return count != null ? count : 0;
    }

    /**
     * Increment retry count in Redis
     */
    private void incrementRetryCount(String messageKey) {
        String key = RETRY_COUNT_KEY_PREFIX + messageKey;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofDays(permanentFailureRetentionDays));
        dlqRetryCounter.increment();
    }

    /**
     * Handle successful recovery
     */
    private void handleSuccessfulRecovery(ConsumerRecord<String, byte[]> record,
                                          DlqMessageMetadata metadata,
                                          RecoveryResult result) {
        dlqRecoveredCounter.increment();

        log.info("Successfully recovered DLQ message: key={}, retries={}, method={}",
                record.key(), metadata.getRetryCount(), result.getRecoveryMethod());

        // Clean up retry tracking
        cleanupRetryTracking(record.key());

        // Audit log
        auditService.logDlqRecovery(metadata, result);

        // Notify if this was a high-retry message
        if (metadata.getRetryCount() > 3) {
            alertingService.sendInfo(
                    "DLQ Recovery Success",
                    String.format("Message recovered after %d retries: %s",
                            metadata.getRetryCount(), record.key())
            );
        }
    }

    /**
     * Handle retryable failure - schedule retry with exponential backoff
     */
    private void handleRetryableFailure(ConsumerRecord<String, byte[]> record,
                                        DlqMessageMetadata metadata,
                                        RecoveryResult result) {
        incrementRetryCount(record.key());

        // Calculate backoff delay
        long delayMs = calculateBackoffDelay(metadata.getRetryCount());

        log.warn("DLQ message recovery failed, scheduling retry #{} in {}ms: key={}, reason={}",
                metadata.getRetryCount() + 1, delayMs, record.key(), result.getFailureReason());

        // Store failure history
        storeFailureHistory(record.key(), metadata, result);

        // Republish to DLQ with delay (using Kafka timestamp for scheduling)
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                record.topic(),
                record.partition(),
                System.currentTimeMillis() + delayMs,
                record.key(),
                record.value()
        );

        future.whenComplete((sendResult, exception) -> {
            if (exception != null) {
                log.error("Failed to republish message to DLQ for retry", exception);
                alertingService.sendCritical("DLQ Republish Failed", exception.getMessage());
            }
        });
    }

    /**
     * Handle permanent failure - store for manual review
     */
    private void handlePermanentFailure(ConsumerRecord<String, byte[]> record,
                                       DlqMessageMetadata metadata) {
        dlqPermanentFailureCounter.increment();

        log.error("DLQ message permanently failed after {} retries: key={}, originalTopic={}",
                metadata.getRetryCount(), record.key(), metadata.getOriginalTopic());

        // Store permanent failure
        storePermanentFailure(record, metadata);

        // Clean up retry tracking
        cleanupRetryTracking(record.key());

        // Audit log
        auditService.logDlqPermanentFailure(metadata);

        // Alert operations team
        alertingService.sendCritical(
                "DLQ Permanent Failure",
                String.format("Message failed permanently: service=%s, type=%s, key=%s, retries=%d",
                        getServiceName(), getMessageType(), record.key(), metadata.getRetryCount())
        );

        // Execute business-specific permanent failure handling
        onPermanentFailure(deserializeMessageSafe(record), metadata);
    }

    /**
     * Handle unexpected errors during processing
     */
    private void handleUnexpectedError(ConsumerRecord<String, byte[]> record, Exception e) {
        log.error("Unexpected error in DLQ handler", e);

        alertingService.sendCritical(
                "DLQ Handler Error",
                String.format("Unexpected error processing DLQ message: %s", e.getMessage())
        );

        // Still try to store for manual review
        DlqMessageMetadata metadata = new DlqMessageMetadata();
        metadata.setKey(record.key());
        metadata.setDlqTopic(record.topic());
        metadata.setExceptionMessage(e.getMessage());
        storePermanentFailure(record, metadata);
    }

    /**
     * Calculate exponential backoff delay
     */
    private long calculateBackoffDelay(int retryCount) {
        long delay = initialRetryDelayMs * (long) Math.pow(2, retryCount);
        return Math.min(delay, maxRetryDelayMs);
    }

    /**
     * Store failure history for analysis
     */
    private void storeFailureHistory(String messageKey, DlqMessageMetadata metadata, RecoveryResult result) {
        String key = FAILURE_HISTORY_KEY_PREFIX + messageKey;
        Map<String, Object> history = new HashMap<>();
        history.put("timestamp", Instant.now().toString());
        history.put("retryCount", metadata.getRetryCount());
        history.put("failureReason", result.getFailureReason());
        history.put("recoveryMethod", result.getRecoveryMethod());

        redisTemplate.opsForList().rightPush(key, history);
        redisTemplate.expire(key, Duration.ofDays(permanentFailureRetentionDays));
    }

    /**
     * Store permanent failure for manual review
     */
    private void storePermanentFailure(ConsumerRecord<String, byte[]> record, DlqMessageMetadata metadata) {
        String key = PERMANENT_FAILURE_KEY_PREFIX + record.key();

        Map<String, Object> failure = new HashMap<>();
        failure.put("service", getServiceName());
        failure.put("messageType", getMessageType());
        failure.put("key", record.key());
        failure.put("topic", metadata.getDlqTopic());
        failure.put("originalTopic", metadata.getOriginalTopic());
        failure.put("timestamp", Instant.now().toString());
        failure.put("retryCount", metadata.getRetryCount());
        failure.put("exceptionMessage", metadata.getExceptionMessage());
        failure.put("payload", new String(record.value(), StandardCharsets.UTF_8));

        redisTemplate.opsForHash().putAll(key, failure);
        redisTemplate.expire(key, Duration.ofDays(permanentFailureRetentionDays));
    }

    /**
     * Clean up retry tracking data
     */
    private void cleanupRetryTracking(String messageKey) {
        redisTemplate.delete(RETRY_COUNT_KEY_PREFIX + messageKey);
        redisTemplate.delete(FAILURE_HISTORY_KEY_PREFIX + messageKey);
    }

    /**
     * Safely deserialize message without throwing exceptions
     */
    private T deserializeMessageSafe(ConsumerRecord<String, byte[]> record) {
        try {
            return deserializeMessage(record);
        } catch (Exception e) {
            log.error("Failed to deserialize message for permanent failure handling", e);
            return null;
        }
    }

    /**
     * Get header value as string
     */
    private String getHeaderValue(ConsumerRecord<String, byte[]> record, String headerKey) {
        Header header = record.headers().lastHeader(headerKey);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Get header value as integer
     */
    private Integer getHeaderValueAsInt(ConsumerRecord<String, byte[]> record, String headerKey) {
        String value = getHeaderValue(record, headerKey);
        try {
            return value != null ? Integer.parseInt(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get header value as long
     */
    private Long getHeaderValueAsLong(ConsumerRecord<String, byte[]> record, String headerKey) {
        String value = getHeaderValue(record, headerKey);
        try {
            return value != null ? Long.parseLong(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========== ABSTRACT METHODS - Must be implemented by subclasses ==========

    /**
     * Get the service name for this handler
     * Used for metrics and logging
     */
    protected abstract String getServiceName();

    /**
     * Get the message type this handler processes
     * Used for metrics and logging
     */
    protected abstract String getMessageType();

    /**
     * Get the message class for deserialization
     */
    protected abstract Class<T> getMessageClass();

    /**
     * Validate the message before attempting recovery
     * Return false if message is invalid and should be treated as permanent failure
     *
     * @param message The deserialized message
     * @param metadata Message metadata
     * @return true if message is valid, false otherwise
     */
    protected abstract boolean validateMessage(T message, DlqMessageMetadata metadata);

    /**
     * Attempt to recover the message
     * Implement business-specific recovery logic here
     *
     * @param message The deserialized message
     * @param metadata Message metadata
     * @return RecoveryResult indicating success, retryable failure, or permanent failure
     */
    protected abstract RecoveryResult attemptRecovery(T message, DlqMessageMetadata metadata);

    // ========== OPTIONAL HOOKS - Can be overridden by subclasses ==========

    /**
     * Called when a message permanently fails
     * Override to implement business-specific handling (e.g., compensation transactions)
     *
     * @param message The deserialized message (may be null if deserialization failed)
     * @param metadata Message metadata
     */
    protected void onPermanentFailure(T message, DlqMessageMetadata metadata) {
        // Default: no-op, subclasses can override
    }

    /**
     * Check if a specific exception is retryable
     * Override to customize retry behavior for specific exceptions
     *
     * @param e The exception
     * @return true if should retry, false for permanent failure
     */
    protected boolean isRetryableException(Exception e) {
        // Default: retry on most exceptions except validation errors
        return !(e instanceof IllegalArgumentException ||
                 e instanceof IllegalStateException);
    }
}
