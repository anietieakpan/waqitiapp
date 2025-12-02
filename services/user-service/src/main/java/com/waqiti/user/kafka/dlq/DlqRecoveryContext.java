package com.waqiti.user.kafka.dlq;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Context information for DLQ recovery operations
 * Provides full audit trail and recovery metadata
 */
@Data
@Builder
public class DlqRecoveryContext {

    /**
     * Original event that failed processing
     */
    private Object originalEvent;

    /**
     * Event type (fully qualified class name)
     */
    private String eventType;

    /**
     * Kafka headers from original message
     */
    private Map<String, Object> headers;

    /**
     * Exception that caused the failure
     */
    private Exception failureException;

    /**
     * Number of retry attempts made
     */
    private int retryAttempts;

    /**
     * Timestamp when message first failed
     */
    private LocalDateTime firstFailureTime;

    /**
     * Timestamp when message entered DLQ
     */
    private LocalDateTime dlqEntryTime;

    /**
     * Kafka topic of original message
     */
    private String originalTopic;

    /**
     * Kafka partition of original message
     */
    private int partition;

    /**
     * Kafka offset of original message
     */
    private long offset;

    /**
     * Consumer group that failed to process
     */
    private String consumerGroup;

    /**
     * Business identifier (userId, transactionId, etc.)
     */
    private String businessIdentifier;

    /**
     * Severity level of the failure
     */
    private DlqSeverityLevel severity;

    /**
     * Recovery strategy to be applied
     */
    private DlqRecoveryStrategy recoveryStrategy;

    /**
     * Additional metadata for recovery
     */
    private Map<String, String> metadata;
}
