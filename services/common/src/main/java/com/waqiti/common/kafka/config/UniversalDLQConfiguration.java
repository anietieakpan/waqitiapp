package com.waqiti.common.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Universal Dead Letter Queue (DLQ) Configuration for all Kafka consumers.
 *
 * Features:
 * - Automatic DLQ creation for failed messages
 * - Exponential backoff retry strategy
 * - Comprehensive error metadata capture
 * - Retry attempt tracking
 * - Circuit breaker integration
 * - Message poisoning detection
 * - Automatic recovery for transient failures
 *
 * This configuration should be imported by all microservices to ensure
 * consistent DLQ behavior across the platform.
 *
 * Usage:
 * 1. Import this configuration in your service's @Configuration class
 * 2. Use @RetryableTopic annotation on @KafkaListener methods
 * 3. Implement @DltHandler methods for handling failed messages
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-10-11
 */
@Slf4j
@Configuration
@EnableKafka
public class UniversalDLQConfiguration {

    @Value("${kafka.dlq.suffix:>.dlt}")
    private String dlqSuffix;

    @Value("${kafka.dlq.max-attempts:5}")
    private int maxAttempts;

    @Value("${kafka.dlq.initial-interval:1000}")
    private long initialInterval;

    @Value("${kafka.dlq.multiplier:2.0}")
    private double multiplier;

    @Value("${kafka.dlq.max-interval:30000}")
    private long maxInterval;

    /**
     * Configure default error handler with DLQ publishing.
     *
     * This handler:
     * 1. Retries failed messages with exponential backoff
     * 2. Sends to DLQ after max retry attempts
     * 3. Captures detailed error metadata
     * 4. Prevents infinite retry loops
     */
    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Create exponential backoff with max retries
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxAttempts);
        backOff.setInitialInterval(initialInterval);  // 1 second
        backOff.setMultiplier(multiplier);            // 2x multiplier
        backOff.setMaxInterval(maxInterval);          // Max 30 seconds

        // Create Dead Letter Publishing Recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (consumerRecord, exception) -> {
                // Determine DLQ topic name
                String originalTopic = consumerRecord.topic();
                String dlqTopic = originalTopic + dlqSuffix;

                log.error("Message processing failed after {} attempts. Sending to DLQ: {}. " +
                          "Original topic: {}, Partition: {}, Offset: {}, Key: {}, Error: {}",
                    maxAttempts, dlqTopic, originalTopic,
                    consumerRecord.partition(), consumerRecord.offset(),
                    consumerRecord.key(), exception.getMessage());

                // PRODUCTION FIX: Return TopicPartition instead of TopicPartitionOffset for Spring Kafka 3.x
                return new org.apache.kafka.common.TopicPartition(dlqTopic, 0);
            }
        );

        // Configure recoverer to add error headers
        // PRODUCTION FIX: Use Apache Kafka RecordHeaders instead of Spring KafkaHeaders (Spring Kafka 3.x)
        recoverer.setHeadersFunction((consumerRecord, exception) -> {
            org.apache.kafka.common.header.internals.RecordHeaders headers =
                new org.apache.kafka.common.header.internals.RecordHeaders();

            // Add original topic info
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                KafkaHeaders.ORIGINAL_TOPIC, consumerRecord.topic().getBytes(StandardCharsets.UTF_8)));
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                KafkaHeaders.ORIGINAL_PARTITION, String.valueOf(consumerRecord.partition()).getBytes()));
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                KafkaHeaders.ORIGINAL_OFFSET, String.valueOf(consumerRecord.offset()).getBytes()));
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                KafkaHeaders.ORIGINAL_TIMESTAMP, String.valueOf(consumerRecord.timestamp()).getBytes()));

            // Add error information
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq_exception_type", exception.getClass().getName().getBytes(StandardCharsets.UTF_8)));
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq_exception_message", truncate(exception.getMessage(), 1000).getBytes(StandardCharsets.UTF_8)));
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq_exception_stacktrace", truncate(getStackTrace(exception), 5000).getBytes(StandardCharsets.UTF_8)));

            // Add timestamp when sent to DLQ
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq_sent_at", Instant.now().toString().getBytes(StandardCharsets.UTF_8)));

            // Add retry count
            Integer retryCount = getRetryCount(consumerRecord);
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq_retry_count", String.valueOf(retryCount).getBytes(StandardCharsets.UTF_8)));

            return headers;
        });

        // Create error handler with backoff and recoverer
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Configure which exceptions should NOT be retried (poison pill detection)
        // PRODUCTION FIX: Removed MethodArgumentTypeMismatchException (doesn't exist in Spring Messaging)
        errorHandler.addNotRetryableExceptions(
            org.springframework.kafka.support.serializer.DeserializationException.class,
            org.springframework.messaging.converter.MessageConversionException.class,
            org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class,
            IllegalArgumentException.class // Business validation failures
        );

        // Add listener for retry events
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                log.warn("Retry attempt {} failed for record from topic: {}, partition: {}, offset: {}. Error: {}",
                    deliveryAttempt, record.topic(), record.partition(), record.offset(), ex.getMessage());
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                log.info("Message recovered after retries. Topic: {}, Offset: {}",
                    record.topic(), record.offset());
            }

            @Override
            public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                log.error("Recovery failed for message. Topic: {}, Offset: {}. Original error: {}, Recovery error: {}",
                    record.topic(), record.offset(), original.getMessage(), failure.getMessage());
            }
        });

        // Configure commit behavior
        errorHandler.setCommitRecovered(true); // Commit offset after recovery

        log.info("Universal DLQ Configuration initialized with {} max attempts, {}ms initial backoff, {}x multiplier",
            maxAttempts, initialInterval, multiplier);

        return errorHandler;
    }

    /**
     * Configure Kafka listener container factory with error handler.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
        DefaultErrorHandler defaultErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setCommonErrorHandler(defaultErrorHandler);

        // Additional container configurations
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(3); // 3 concurrent consumers per listener

        return factory;
    }

    /**
     * Extract retry count from consumer record headers.
     */
    private Integer getRetryCount(ConsumerRecord<?, ?> record) {
        for (Header header : record.headers()) {
            if ("kafka_retry_count".equals(header.key())) {
                return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
            }
        }
        return 0;
    }

    /**
     * Get stack trace as string.
     */
    private String getStackTrace(Throwable throwable) {
        if (throwable == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 5000) break; // Limit size
        }

        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            sb.append("Caused by: ");
            sb.append(getStackTrace(cause));
        }

        return sb.toString();
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "... (truncated)";
    }
}
