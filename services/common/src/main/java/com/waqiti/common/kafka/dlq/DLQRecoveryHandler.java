package com.waqiti.common.kafka.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Base DLQ recovery handler for processing failed messages
 * Provides exponential backoff, max retry logic, and audit trail
 */
@Component
@RequiredArgsConstructor
@Slf4j
public abstract class DLQRecoveryHandler<K, V> {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 60000;

    private final KafkaTemplate<K, V> kafkaTemplate;
    private final DLQAuditRepository auditRepository;

    /**
     * Process a message from DLQ with retry logic
     */
    public void processFromDLQ(ConsumerRecord<K, V> record) {
        String messageId = extractMessageId(record);
        int attemptNumber = getAttemptNumber(record);

        log.info("Processing DLQ message: topic={}, partition={}, offset={}, attemptNumber={}",
            record.topic(), record.partition(), record.offset(), attemptNumber);

        try {
            // Check if max retries exceeded
            if (attemptNumber >= MAX_RETRY_ATTEMPTS) {
                log.error("Max retry attempts exceeded for message: {}", messageId);
                moveToDeadLetterStorage(record);
                return;
            }

            // Validate message
            if (!validateMessage(record.value())) {
                log.error("Message validation failed: {}", messageId);
                moveToDeadLetterStorage(record);
                return;
            }

            // Process the message
            boolean success = processMessage(record.value());

            if (success) {
                log.info("Successfully recovered message from DLQ: {}", messageId);
                auditRepository.recordSuccess(messageId, record.topic(), attemptNumber);
            } else {
                // Retry with exponential backoff
                retryWithBackoff(record, attemptNumber);
            }

        } catch (Exception e) {
            log.error("Error processing DLQ message: {}", messageId, e);
            retryWithBackoff(record, attemptNumber);
        }
    }

    /**
     * Retry message with exponential backoff
     */
    private void retryWithBackoff(ConsumerRecord<K, V> record, int attemptNumber) {
        long backoffMs = Math.min(
            INITIAL_BACKOFF_MS * (long) Math.pow(2, attemptNumber),
            MAX_BACKOFF_MS
        );

        log.info("Retrying message after {} ms (attempt {})", backoffMs, attemptNumber + 1);

        try {
            Thread.sleep(backoffMs);

            // Add retry metadata to headers
            Map<String, String> metadata = new HashMap<>();
            metadata.put("retry-attempt", String.valueOf(attemptNumber + 1));
            metadata.put("retry-timestamp", Instant.now().toString());
            metadata.put("original-topic", record.topic());

            // Send back to original topic for retry
            String originalTopic = getOriginalTopic(record);
            kafkaTemplate.send(originalTopic, record.key(), record.value());

            auditRepository.recordRetry(
                extractMessageId(record),
                originalTopic,
                attemptNumber + 1,
                backoffMs
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Retry interrupted", e);
        } catch (Exception e) {
            log.error("Failed to retry message", e);
        }
    }

    /**
     * Move message to permanent dead letter storage
     */
    private void moveToDeadLetterStorage(ConsumerRecord<K, V> record) {
        String messageId = extractMessageId(record);

        try {
            DLQDeadLetter deadLetter = DLQDeadLetter.builder()
                .messageId(messageId)
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .key(serializeKey(record.key()))
                .value(serializeValue(record.value()))
                .headers(extractHeaders(record))
                .failureReason(getFailureReason(record))
                .timestamp(Instant.now())
                .build();

            auditRepository.saveDeadLetter(deadLetter);

            log.info("Moved message to permanent dead letter storage: {}", messageId);

        } catch (Exception e) {
            log.error("Failed to move message to dead letter storage: {}", messageId, e);
        }
    }

    /**
     * Abstract methods to be implemented by specific handlers
     */
    protected abstract boolean processMessage(V message);

    protected abstract boolean validateMessage(V message);

    protected abstract String extractMessageId(ConsumerRecord<K, V> record);

    protected abstract String getOriginalTopic(ConsumerRecord<K, V> record);

    protected abstract String serializeKey(K key);

    protected abstract String serializeValue(V value);

    protected abstract Map<String, String> extractHeaders(ConsumerRecord<K, V> record);

    protected abstract String getFailureReason(ConsumerRecord<K, V> record);

    /**
     * Extract retry attempt number from headers
     */
    private int getAttemptNumber(ConsumerRecord<K, V> record) {
        try {
            String attemptStr = new String(
                record.headers().lastHeader("retry-attempt").value()
            );
            return Integer.parseInt(attemptStr);
        } catch (Exception e) {
            return 0;
        }
    }
}
