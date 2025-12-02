package com.waqiti.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Dead Letter Queue Handler for Kafka Message Processing
 *
 * Handles failed Kafka messages by:
 * 1. Enriching with failure metadata (exception, timestamp, retry count)
 * 2. Publishing to topic-specific DLQ (original-topic.dlq)
 * 3. Logging for monitoring/alerting
 * 4. Supporting manual reprocessing via DLQ consumers
 *
 * Pattern: Dead Letter Queue for event-driven resilience
 * PCI DSS 12.10.1: Audit logging of all failures
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DLQHandler {

    private static final String DLQ_SUFFIX = ".dlq";
    private static final String HEADER_EXCEPTION = "dlq-exception";
    private static final String HEADER_EXCEPTION_MESSAGE = "dlq-exception-message";
    private static final String HEADER_EXCEPTION_STACKTRACE = "dlq-exception-stacktrace";
    private static final String HEADER_ORIGINAL_TOPIC = "dlq-original-topic";
    private static final String HEADER_ORIGINAL_PARTITION = "dlq-original-partition";
    private static final String HEADER_ORIGINAL_OFFSET = "dlq-original-offset";
    private static final String HEADER_RETRY_COUNT = "dlq-retry-count";
    private static final String HEADER_FAILURE_TIMESTAMP = "dlq-failure-timestamp";
    private static final String HEADER_CONSUMER_GROUP = "dlq-consumer-group";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Send failed message to Dead Letter Queue with comprehensive metadata
     *
     * @param record Original Kafka record that failed processing
     * @param exception Exception that caused the failure
     * @param consumerGroup Consumer group that failed processing
     * @return CompletableFuture for async DLQ publishing
     */
    public CompletableFuture<SendResult<String, Object>> sendToDLQ(
            ConsumerRecord<String, ?> record,
            Exception exception,
            String consumerGroup) {

        String dlqTopic = record.topic() + DLQ_SUFFIX;
        int retryCount = extractRetryCount(record);

        // Build DLQ record with enriched headers
        ProducerRecord<String, Object> dlqRecord = new ProducerRecord<>(
            dlqTopic,
            null, // partition - let Kafka decide
            record.key(),
            record.value()
        );

        // Add failure metadata headers
        addHeader(dlqRecord, HEADER_EXCEPTION, exception.getClass().getName());
        addHeader(dlqRecord, HEADER_EXCEPTION_MESSAGE, truncate(exception.getMessage(), 1024));
        addHeader(dlqRecord, HEADER_EXCEPTION_STACKTRACE, truncate(getStackTrace(exception), 4096));
        addHeader(dlqRecord, HEADER_ORIGINAL_TOPIC, record.topic());
        addHeader(dlqRecord, HEADER_ORIGINAL_PARTITION, String.valueOf(record.partition()));
        addHeader(dlqRecord, HEADER_ORIGINAL_OFFSET, String.valueOf(record.offset()));
        addHeader(dlqRecord, HEADER_RETRY_COUNT, String.valueOf(retryCount + 1));
        addHeader(dlqRecord, HEADER_FAILURE_TIMESTAMP, Instant.now().toString());
        addHeader(dlqRecord, HEADER_CONSUMER_GROUP, consumerGroup);

        // Copy original headers (preserving trace context, etc.)
        record.headers().forEach(header ->
            dlqRecord.headers().add(header.key(), header.value())
        );

        // Log for monitoring/alerting
        log.error("Sending message to DLQ: topic={}, partition={}, offset={}, retryCount={}, exception={}, consumerGroup={}",
            record.topic(), record.partition(), record.offset(), retryCount + 1,
            exception.getClass().getSimpleName(), consumerGroup, exception);

        // Publish to DLQ asynchronously
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(dlqRecord);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("CRITICAL: Failed to send message to DLQ topic={}, key={}",
                    dlqTopic, record.key(), ex);
                // Fallback: Log to database or external monitoring system
                logDLQFailure(record, exception, consumerGroup);
            } else {
                log.info("Successfully sent message to DLQ: topic={}, partition={}, offset={}",
                    dlqTopic, result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    /**
     * Extract retry count from record headers (for retry tracking)
     */
    private int extractRetryCount(ConsumerRecord<String, ?> record) {
        var retryHeader = record.headers().lastHeader(HEADER_RETRY_COUNT);
        if (retryHeader != null) {
            try {
                return Integer.parseInt(new String(retryHeader.value()));
            } catch (NumberFormatException e) {
                log.warn("Invalid retry count header, defaulting to 0", e);
            }
        }
        return 0;
    }

    /**
     * Add header to producer record
     */
    private void addHeader(ProducerRecord<String, Object> record, String key, String value) {
        if (value != null) {
            record.headers().add(key, value.getBytes());
        }
    }

    /**
     * Extract stack trace as string
     */
    private String getStackTrace(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 4096) break; // Prevent excessive stack traces
        }

        if (exception.getCause() != null) {
            sb.append("Caused by: ").append(exception.getCause().getClass().getName())
              .append(": ").append(exception.getCause().getMessage());
        }

        return sb.toString();
    }

    /**
     * Truncate string to max length
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Fallback logging when DLQ publishing fails (critical path)
     */
    private void logDLQFailure(ConsumerRecord<String, ?> record, Exception exception, String consumerGroup) {
        try {
            Map<String, Object> failureLog = new HashMap<>();
            failureLog.put("topic", record.topic());
            failureLog.put("partition", record.partition());
            failureLog.put("offset", record.offset());
            failureLog.put("key", record.key());
            failureLog.put("exception", exception.getClass().getName());
            failureLog.put("message", exception.getMessage());
            failureLog.put("consumerGroup", consumerGroup);
            failureLog.put("timestamp", Instant.now().toString());

            // Log as JSON for external log aggregation (ELK, Datadog, etc.)
            String json = objectMapper.writeValueAsString(failureLog);
            log.error("DLQ_FAILURE_FALLBACK: {}", json);

            // TODO: Publish to external dead letter store (S3, database, etc.)
            // persistToDatabaseDLQ(failureLog);

        } catch (Exception e) {
            log.error("CATASTROPHIC: Failed to log DLQ failure fallback", e);
        }
    }

    /**
     * Check if message should be retried or sent to DLQ
     *
     * @param record Kafka record
     * @param maxRetries Maximum retry attempts before DLQ
     * @return true if should retry, false if should send to DLQ
     */
    public boolean shouldRetry(ConsumerRecord<String, ?> record, int maxRetries) {
        int retryCount = extractRetryCount(record);
        return retryCount < maxRetries;
    }

    /**
     * Get DLQ topic name for a given source topic
     */
    public String getDLQTopicName(String sourceTopic) {
        return sourceTopic + DLQ_SUFFIX;
    }
}
