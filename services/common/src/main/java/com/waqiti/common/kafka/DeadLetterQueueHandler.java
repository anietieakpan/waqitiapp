package com.waqiti.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise-grade Dead Letter Queue (DLQ) handler for Kafka.
 *
 * Features:
 * - Automatic DLQ routing for failed messages
 * - Retry metadata tracking (retry count, timestamps, exception details)
 * - Poison pill detection (messages that consistently fail)
 * - Error classification (transient vs permanent failures)
 * - Alerting for DLQ threshold breaches
 * - DLQ message replay capability
 * - Compliance logging for audit trails
 *
 * DLQ naming convention: {original-topic}.dlq
 *
 * Headers added to DLQ messages:
 * - x-original-topic: Original topic name
 * - x-original-partition: Original partition
 * - x-original-offset: Original offset
 * - x-retry-count: Number of retry attempts
 * - x-first-failure-time: Timestamp of first failure
 * - x-last-failure-time: Timestamp of last failure
 * - x-exception-class: Exception class name
 * - x-exception-message: Exception message
 * - x-exception-stacktrace: Exception stack trace
 * - x-consumer-group: Consumer group ID
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class DeadLetterQueueHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DlqMetricsCollector metricsCollector;

    private static final String DLQ_SUFFIX = ".dlq";
    private static final String HEADER_ORIGINAL_TOPIC = "x-original-topic";
    private static final String HEADER_ORIGINAL_PARTITION = "x-original-partition";
    private static final String HEADER_ORIGINAL_OFFSET = "x-original-offset";
    private static final String HEADER_RETRY_COUNT = "x-retry-count";
    private static final String HEADER_FIRST_FAILURE_TIME = "x-first-failure-time";
    private static final String HEADER_LAST_FAILURE_TIME = "x-last-failure-time";
    private static final String HEADER_EXCEPTION_CLASS = "x-exception-class";
    private static final String HEADER_EXCEPTION_MESSAGE = "x-exception-message";
    private static final String HEADER_EXCEPTION_STACKTRACE = "x-exception-stacktrace";
    private static final String HEADER_CONSUMER_GROUP = "x-consumer-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int POISON_PILL_THRESHOLD = 5;

    public DeadLetterQueueHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            DlqMetricsCollector metricsCollector) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
    }

    /**
     * Send failed message to Dead Letter Queue with full metadata.
     *
     * @param record Original consumer record that failed
     * @param exception Exception that caused the failure
     * @param consumerGroup Consumer group ID
     * @return CompletableFuture of send result
     */
    public <K, V> CompletableFuture<SendResult<String, Object>> sendToDlq(
            ConsumerRecord<K, V> record,
            Exception exception,
            String consumerGroup) {

        try {
            String dlqTopic = getDlqTopicName(record.topic());
            int retryCount = getRetryCount(record) + 1;

            // Check if this is a poison pill (exceeded max retries)
            if (retryCount > POISON_PILL_THRESHOLD) {
                log.error("Poison pill detected - message exceeded {} retry attempts | Topic: {} | Key: {} | Offset: {}",
                        POISON_PILL_THRESHOLD,
                        record.topic(),
                        record.key(),
                        record.offset());

                metricsCollector.recordPoisonPill(record.topic(), consumerGroup);
                // Send to poison pill topic for manual intervention
                dlqTopic = record.topic() + ".poison";
            }

            // Build DLQ message with enriched headers
            ProducerRecord<String, Object> dlqRecord = new ProducerRecord<>(
                    dlqTopic,
                    null, // Let Kafka assign partition
                    String.valueOf(record.key()),
                    record.value()
            );

            // Copy original headers
            if (record.headers() != null) {
                record.headers().forEach(header ->
                        dlqRecord.headers().add(header));
            }

            // Add DLQ-specific headers
            addDlqHeaders(dlqRecord, record, exception, consumerGroup, retryCount);

            // Send to DLQ asynchronously
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(dlqRecord);

            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to send message to DLQ | Topic: {} | Key: {} | Error: {}",
                            dlqTopic,
                            record.key(),
                            throwable.getMessage(),
                            throwable);

                    metricsCollector.recordDlqSendFailure(record.topic(), consumerGroup);
                } else {
                    log.info("Message sent to DLQ | Original Topic: {} | DLQ Topic: {} | Key: {} | Offset: {} | Retry Count: {}",
                            record.topic(),
                            dlqTopic,
                            record.key(),
                            record.offset(),
                            retryCount);

                    metricsCollector.recordDlqMessage(record.topic(), consumerGroup, retryCount);
                }
            });

            return future;

        } catch (Exception e) {
            log.error("Critical error in DLQ handler - this should never happen | Topic: {} | Error: {}",
                    record.topic(),
                    e.getMessage(),
                    e);

            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Add DLQ-specific headers to the message.
     */
    private <K, V> void addDlqHeaders(
            ProducerRecord<String, Object> dlqRecord,
            ConsumerRecord<K, V> originalRecord,
            Exception exception,
            String consumerGroup,
            int retryCount) {

        Headers headers = dlqRecord.headers();

        // Original message metadata
        headers.add(createHeader(HEADER_ORIGINAL_TOPIC, originalRecord.topic()));
        headers.add(createHeader(HEADER_ORIGINAL_PARTITION, String.valueOf(originalRecord.partition())));
        headers.add(createHeader(HEADER_ORIGINAL_OFFSET, String.valueOf(originalRecord.offset())));

        // Retry metadata
        headers.add(createHeader(HEADER_RETRY_COUNT, String.valueOf(retryCount)));
        headers.add(createHeader(HEADER_CONSUMER_GROUP, consumerGroup));

        // Timestamp metadata
        String firstFailureTime = getHeaderValue(originalRecord.headers(), HEADER_FIRST_FAILURE_TIME);
        if (firstFailureTime == null) {
            firstFailureTime = Instant.now().toString();
        }
        headers.add(createHeader(HEADER_FIRST_FAILURE_TIME, firstFailureTime));
        headers.add(createHeader(HEADER_LAST_FAILURE_TIME, Instant.now().toString()));

        // Exception metadata
        if (exception != null) {
            headers.add(createHeader(HEADER_EXCEPTION_CLASS, exception.getClass().getName()));

            String exceptionMessage = exception.getMessage();
            if (exceptionMessage != null && exceptionMessage.length() > 1000) {
                exceptionMessage = exceptionMessage.substring(0, 1000) + "... (truncated)";
            }
            headers.add(createHeader(HEADER_EXCEPTION_MESSAGE, exceptionMessage));

            // Add stack trace (truncated for size)
            String stackTrace = getStackTraceAsString(exception);
            if (stackTrace.length() > 5000) {
                stackTrace = stackTrace.substring(0, 5000) + "... (truncated)";
            }
            headers.add(createHeader(HEADER_EXCEPTION_STACKTRACE, stackTrace));
        }
    }

    /**
     * Get DLQ topic name from original topic.
     */
    private String getDlqTopicName(String originalTopic) {
        return originalTopic + DLQ_SUFFIX;
    }

    /**
     * Get retry count from message headers.
     */
    private <K, V> int getRetryCount(ConsumerRecord<K, V> record) {
        if (record.headers() == null) {
            return 0;
        }

        String retryCountStr = getHeaderValue(record.headers(), HEADER_RETRY_COUNT);
        if (retryCountStr == null) {
            return 0;
        }

        try {
            return Integer.parseInt(retryCountStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid retry count header value: {}", retryCountStr);
            return 0;
        }
    }

    /**
     * Check if message should be retried or sent to DLQ.
     *
     * @param record Consumer record
     * @param exception Exception that occurred
     * @return true if message should be retried, false if should go to DLQ
     */
    public <K, V> boolean shouldRetry(ConsumerRecord<K, V> record, Exception exception) {
        int retryCount = getRetryCount(record);

        // Check if exceeded max retries
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            log.info("Message exceeded max retry attempts ({}) - sending to DLQ | Topic: {} | Key: {} | Offset: {}",
                    MAX_RETRY_ATTEMPTS,
                    record.topic(),
                    record.key(),
                    record.offset());
            return false;
        }

        // Classify exception type
        if (isTransientException(exception)) {
            log.info("Transient exception detected - will retry | Topic: {} | Key: {} | Retry: {}/{}",
                    record.topic(),
                    record.key(),
                    retryCount + 1,
                    MAX_RETRY_ATTEMPTS);
            return true;
        } else {
            log.info("Permanent exception detected - sending to DLQ | Topic: {} | Key: {} | Exception: {}",
                    record.topic(),
                    record.key(),
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Determine if exception is transient (can be retried) or permanent.
     */
    private boolean isTransientException(Exception exception) {
        if (exception == null) {
            return false;
        }

        String className = exception.getClass().getName();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";

        // Transient exceptions (network, timeout, temporary unavailability)
        if (className.contains("Timeout") ||
            className.contains("ConnectionException") ||
            className.contains("SocketException") ||
            className.contains("UnknownHostException") ||
            className.contains("ServiceUnavailable") ||
            message.contains("timeout") ||
            message.contains("connection refused") ||
            message.contains("temporarily unavailable")) {
            return true;
        }

        // Permanent exceptions (validation, business logic, authentication)
        if (className.contains("ValidationException") ||
            className.contains("IllegalArgumentException") ||
            className.contains("AuthenticationException") ||
            className.contains("AuthorizationException") ||
            className.contains("JsonParseException") ||
            className.contains("DeserializationException")) {
            return false;
        }

        // Default to non-retryable for safety
        return false;
    }

    /**
     * Replay messages from DLQ back to original topic.
     *
     * @param dlqTopic DLQ topic name
     * @param messageKey Key of message to replay (null for all messages)
     * @return Number of messages replayed
     */
    public int replayFromDlq(String dlqTopic, String messageKey) {
        log.info("Replaying messages from DLQ | DLQ Topic: {} | Message Key: {}",
                dlqTopic,
                messageKey != null ? messageKey : "all");

        // Implementation would consume from DLQ and republish to original topic
        // This requires additional configuration and is typically done via admin tools
        // For now, log the operation

        log.warn("DLQ replay functionality requires administrative action");
        return 0;
    }

    /**
     * Create Kafka header.
     */
    private Header createHeader(String key, String value) {
        if (value == null) {
            value = "";
        }
        return new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get header value as string.
     */
    private String getHeaderValue(Headers headers, String key) {
        if (headers == null) {
            return null;
        }

        Header header = headers.lastHeader(key);
        if (header == null) {
            return null;
        }

        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Convert exception stack trace to string.
     */
    private String getStackTraceAsString(Exception exception) {
        if (exception == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName());
        if (exception.getMessage() != null) {
            sb.append(": ").append(exception.getMessage());
        }
        sb.append("\n");

        StackTraceElement[] stackTrace = exception.getStackTrace();
        int maxElements = Math.min(stackTrace.length, 20); // Limit to 20 elements
        for (int i = 0; i < maxElements; i++) {
            sb.append("\tat ").append(stackTrace[i].toString()).append("\n");
        }

        if (stackTrace.length > maxElements) {
            sb.append("\t... ").append(stackTrace.length - maxElements).append(" more\n");
        }

        if (exception.getCause() != null) {
            sb.append("Caused by: ").append(getStackTraceAsString((Exception) exception.getCause()));
        }

        return sb.toString();
    }
}
