package com.waqiti.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Base Kafka Consumer
 * Provides common functionality for Kafka consumers with generic type support
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Slf4j
public abstract class BaseKafkaConsumer<T> {

    protected final ObjectMapper objectMapper;
    protected final String topic;

    protected BaseKafkaConsumer(ObjectMapper objectMapper, String topic) {
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    /**
     * Handle dead letter processing
     */
    protected void handleDeadLetter(String topic, String message, Exception error) {
        log.error("Dead letter event for topic {}: {}", topic, error.getMessage(), error);
        // Implementation would send to DLQ
    }

    /**
     * Deserialize event from JSON string
     */
    protected T deserializeEvent(String eventJson, Class<T> eventClass) {
        try {
            return objectMapper.readValue(eventJson, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize event: {}", e.getMessage(), e);
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    /**
     * Validate event (override in subclasses for custom validation)
     */
    protected void validateEvent(T event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        // Subclasses can override for additional validation
    }

    /**
     * Build event context from consumer record
     */
    protected EventContext buildEventContext(ConsumerRecord<String, String> record, String correlationId) {
        return EventContext.builder()
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .timestamp(record.timestamp())
                .key(record.key())
                .correlationId(correlationId)
                .headers(extractHeaders(record))
                .build();
    }

    /**
     * Handle processing failure
     */
    protected void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        log.error("Processing failure for record - Topic: {}, Partition: {}, Offset: {}, CorrelationId: {}, Error: {}",
                record.topic(), record.partition(), record.offset(), correlationId, error.getMessage(), error);

        // Send to DLQ or error handling mechanism
        handleDeadLetter(record.topic(), record.value(), error);
    }

    /**
     * Extract headers from consumer record
     */
    private Map<String, String> extractHeaders(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new HashMap<>();
        record.headers().forEach(header -> {
            if (header.value() != null) {
                headers.put(header.key(), new String(header.value()));
            }
        });
        return headers;
    }

    /**
     * Event Context - Contains metadata about the Kafka event
     */
    @Data
    @Builder
    public static class EventContext {
        private String topic;
        private int partition;
        private long offset;
        private long timestamp;
        private String key;
        private String correlationId;
        private Map<String, String> headers;

        public Instant getTimestampAsInstant() {
            return Instant.ofEpochMilli(timestamp);
        }
    }
}
