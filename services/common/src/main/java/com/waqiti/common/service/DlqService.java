package com.waqiti.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.DlqMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Dead Letter Queue Service
 * Handles sending failed messages to DLQ topics for retry/analysis
 *
 * Used by Kafka consumers across all services for handling message processing failures
 */
@Service
public class DlqService {

    private static final Logger logger = LoggerFactory.getLogger(DlqService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DlqService(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a failed message to the Dead Letter Queue
     *
     * @param dlqTopic The DLQ topic name
     * @param originalMessage The original message that failed
     * @param errorMessage The error message describing the failure
     * @param requestId Optional request ID for tracing
     */
    public void sendToDlq(String dlqTopic, Object originalMessage, String errorMessage, String requestId) {
        try {
            DlqMessage dlqMessage = DlqMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .originalMessage(objectMapper.writeValueAsString(originalMessage))
                    .errorMessage(errorMessage)
                    .requestId(requestId)
                    .timestamp(Instant.now())
                    .retryCount(0)
                    .build();

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(dlqTopic, dlqMessage);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to send message to DLQ topic: {}, requestId: {}", dlqTopic, requestId, ex);
                } else {
                    logger.info("Successfully sent message to DLQ topic: {}, requestId: {}", dlqTopic, requestId);
                }
            });

        } catch (Exception e) {
            logger.error("Exception while sending to DLQ topic: {}, requestId: {}", dlqTopic, requestId, e);
        }
    }

    /**
     * Send a failed message to the Dead Letter Queue with additional metadata
     *
     * @param dlqTopic The DLQ topic name
     * @param originalMessage The original message that failed
     * @param errorMessage The error message describing the failure
     * @param requestId Optional request ID for tracing
     * @param metadata Additional metadata about the failure
     */
    public void sendToDlq(String dlqTopic, Object originalMessage, String errorMessage, String requestId, Map<String, String> metadata) {
        try {
            DlqMessage dlqMessage = DlqMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .originalMessage(objectMapper.writeValueAsString(originalMessage))
                    .errorMessage(errorMessage)
                    .requestId(requestId)
                    .timestamp(Instant.now())
                    .retryCount(0)
                    .metadata(metadata)
                    .build();

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(dlqTopic, dlqMessage);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to send message to DLQ topic: {}, requestId: {}", dlqTopic, requestId, ex);
                } else {
                    logger.info("Successfully sent message to DLQ topic: {}, requestId: {}, metadata: {}", dlqTopic, requestId, metadata);
                }
            });

        } catch (Exception e) {
            logger.error("Exception while sending to DLQ topic: {}, requestId: {}", dlqTopic, requestId, e);
        }
    }

    /**
     * Send a failed message to DLQ with retry count
     *
     * @param dlqTopic The DLQ topic name
     * @param originalMessage The original message that failed
     * @param errorMessage The error message describing the failure
     * @param requestId Optional request ID for tracing
     * @param retryCount Number of times this message has been retried
     */
    public void sendToDlqWithRetry(String dlqTopic, Object originalMessage, String errorMessage, String requestId, int retryCount) {
        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("retryCount", String.valueOf(retryCount));

            DlqMessage dlqMessage = DlqMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .originalMessage(objectMapper.writeValueAsString(originalMessage))
                    .errorMessage(errorMessage)
                    .requestId(requestId)
                    .timestamp(Instant.now())
                    .retryCount(retryCount)
                    .metadata(metadata)
                    .build();

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(dlqTopic, dlqMessage);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to send message to DLQ topic: {}, requestId: {}, retryCount: {}", dlqTopic, requestId, retryCount, ex);
                } else {
                    logger.info("Successfully sent message to DLQ topic: {}, requestId: {}, retryCount: {}", dlqTopic, requestId, retryCount);
                }
            });

        } catch (Exception e) {
            logger.error("Exception while sending to DLQ topic: {}, requestId: {}, retryCount: {}", dlqTopic, requestId, retryCount, e);
        }
    }

    /**
     * Retry a message from the DLQ to its original topic
     *
     * @param originalTopic The original topic to retry to
     * @param dlqMessage The DLQ message to retry
     */
    public void retryFromDlq(String originalTopic, DlqMessage dlqMessage) {
        try {
            Object originalMessage = objectMapper.readValue(dlqMessage.getOriginalMessage(), Object.class);

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(originalTopic, originalMessage);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to retry message from DLQ to topic: {}, requestId: {}", originalTopic, dlqMessage.getRequestId(), ex);
                    // Send back to DLQ with incremented retry count
                    sendToDlqWithRetry(originalTopic + ".dlq", originalMessage,
                            "Retry failed: " + ex.getMessage(),
                            dlqMessage.getRequestId(),
                            dlqMessage.getRetryCount() + 1);
                } else {
                    logger.info("Successfully retried message from DLQ to topic: {}, requestId: {}", originalTopic, dlqMessage.getRequestId());
                }
            });

        } catch (Exception e) {
            logger.error("Exception while retrying from DLQ to topic: {}, requestId: {}", originalTopic, dlqMessage.getRequestId(), e);
        }
    }

    /**
     * Get DLQ message by ID (if needed for retrieval/analysis)
     * Note: This is a placeholder - actual implementation would query DLQ storage
     *
     * @param dlqMessageId The DLQ message ID
     * @return The DLQ message or null if not found
     */
    public DlqMessage getDlqMessage(String dlqMessageId) {
        // Placeholder - actual implementation would query from DLQ storage/database
        logger.warn("getDlqMessage not fully implemented - requires DLQ storage backend");
        return null;
    }
}
