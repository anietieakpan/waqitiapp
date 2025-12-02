package com.waqiti.common.constants;

/**
 * Kafka Constants
 *
 * Centralized Kafka-related constants used across the platform.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-17
 */
public final class KafkaConstants {

    private KafkaConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== Message Keys ==========

    /**
     * Standard message key for received Kafka messages
     */
    public static final String RECEIVED_MESSAGE_KEY = "kafka.received.message";

    /**
     * Message key for message headers
     */
    public static final String MESSAGE_HEADERS_KEY = "kafka.message.headers";

    /**
     * Message key for correlation ID
     */
    public static final String CORRELATION_ID_KEY = "kafka.correlation.id";

    /**
     * Message key for timestamp
     */
    public static final String TIMESTAMP_KEY = "kafka.timestamp";

    /**
     * Message key for partition
     */
    public static final String PARTITION_KEY = "kafka.partition";

    /**
     * Message key for offset
     */
    public static final String OFFSET_KEY = "kafka.offset";

    /**
     * Message key for topic
     */
    public static final String TOPIC_KEY = "kafka.topic";

    /**
     * Message key for consumer group
     */
    public static final String CONSUMER_GROUP_KEY = "kafka.consumer.group";

    // ========== Header Keys ==========

    /**
     * Header for event type
     */
    public static final String HEADER_EVENT_TYPE = "event-type";

    /**
     * Header for event version
     */
    public static final String HEADER_EVENT_VERSION = "event-version";

    /**
     * Header for source service
     */
    public static final String HEADER_SOURCE_SERVICE = "source-service";

    /**
     * Header for trace ID
     */
    public static final String HEADER_TRACE_ID = "trace-id";

    /**
     * Header for span ID
     */
    public static final String HEADER_SPAN_ID = "span-id";

    /**
     * Header for user ID
     */
    public static final String HEADER_USER_ID = "user-id";

    /**
     * Header for tenant ID
     */
    public static final String HEADER_TENANT_ID = "tenant-id";

    /**
     * Header for idempotency key
     */
    public static final String HEADER_IDEMPOTENCY_KEY = "idempotency-key";

    // ========== Consumer Configuration ==========

    /**
     * Default consumer group prefix
     */
    public static final String DEFAULT_CONSUMER_GROUP_PREFIX = "waqiti";

    /**
     * Default concurrency for consumers
     */
    public static final int DEFAULT_CONSUMER_CONCURRENCY = 3;

    /**
     * Default max poll records
     */
    public static final int DEFAULT_MAX_POLL_RECORDS = 500;

    /**
     * Default poll timeout in milliseconds
     */
    public static final long DEFAULT_POLL_TIMEOUT_MS = 3000L;

    // ========== Producer Configuration ==========

    /**
     * Default batch size
     */
    public static final int DEFAULT_BATCH_SIZE = 16384;

    /**
     * Default linger time in milliseconds
     */
    public static final long DEFAULT_LINGER_MS = 10L;

    /**
     * Default compression type
     */
    public static final String DEFAULT_COMPRESSION_TYPE = "snappy";

    /**
     * Default acks configuration
     */
    public static final String DEFAULT_ACKS = "all";

    // ========== Retry Configuration ==========

    /**
     * Default number of retries
     */
    public static final int DEFAULT_RETRIES = 3;

    /**
     * Default retry backoff in milliseconds
     */
    public static final long DEFAULT_RETRY_BACKOFF_MS = 1000L;

    /**
     * Maximum retry backoff in milliseconds
     */
    public static final long MAX_RETRY_BACKOFF_MS = 32000L;

    // ========== Dead Letter Queue ==========

    /**
     * DLQ suffix for topics
     */
    public static final String DLQ_SUFFIX = "-dlt";

    /**
     * DLQ header for original topic
     */
    public static final String DLQ_HEADER_ORIGINAL_TOPIC = "dlq.original.topic";

    /**
     * DLQ header for exception message
     */
    public static final String DLQ_HEADER_EXCEPTION_MESSAGE = "dlq.exception.message";

    /**
     * DLQ header for exception stacktrace
     */
    public static final String DLQ_HEADER_EXCEPTION_STACKTRACE = "dlq.exception.stacktrace";

    /**
     * DLQ header for retry count
     */
    public static final String DLQ_HEADER_RETRY_COUNT = "dlq.retry.count";

    /**
     * DLQ header for original timestamp
     */
    public static final String DLQ_HEADER_ORIGINAL_TIMESTAMP = "dlq.original.timestamp";

    // ========== Monitoring ==========

    /**
     * Metric name for messages consumed
     */
    public static final String METRIC_MESSAGES_CONSUMED = "kafka.messages.consumed";

    /**
     * Metric name for messages produced
     */
    public static final String METRIC_MESSAGES_PRODUCED = "kafka.messages.produced";

    /**
     * Metric name for consumer lag
     */
    public static final String METRIC_CONSUMER_LAG = "kafka.consumer.lag";

    /**
     * Metric name for processing time
     */
    public static final String METRIC_PROCESSING_TIME = "kafka.processing.time";

    /**
     * Metric name for DLQ messages
     */
    public static final String METRIC_DLQ_MESSAGES = "kafka.dlq.messages";

    // ========== Error Handling ==========

    /**
     * Maximum DLQ retry attempts
     */
    public static final int MAX_DLQ_RETRY_ATTEMPTS = 3;

    /**
     * Timeout for message processing in milliseconds
     */
    public static final long MESSAGE_PROCESSING_TIMEOUT_MS = 30000L;

    /**
     * Session timeout in milliseconds
     */
    public static final long SESSION_TIMEOUT_MS = 30000L;

    /**
     * Heartbeat interval in milliseconds
     */
    public static final long HEARTBEAT_INTERVAL_MS = 10000L;
}
