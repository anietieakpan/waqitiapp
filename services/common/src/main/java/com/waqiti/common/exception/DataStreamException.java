package com.waqiti.common.exception;

/**
 * Exception thrown when data streaming operations fail.
 *
 * Common causes:
 * - Kafka stream processing errors
 * - Stream topology failures
 * - Data deserialization errors
 * - Stream state store errors
 * - Window processing failures
 *
 * This should trigger:
 * - Retry for transient errors
 * - Alert data engineering team
 * - DLQ processing for poison pills
 * - Stream application restart if needed
 *
 * @author Waqiti Platform
 */
public class DataStreamException extends RuntimeException {

    private final String streamName;
    private final String topicName;
    private final String operation;
    private final boolean retryable;

    /**
     * Creates exception with message
     */
    public DataStreamException(String message) {
        super(message);
        this.streamName = null;
        this.topicName = null;
        this.operation = null;
        this.retryable = true;
    }

    /**
     * Creates exception with message and cause
     */
    public DataStreamException(String message, Throwable cause) {
        super(message, cause);
        this.streamName = null;
        this.topicName = null;
        this.operation = null;
        this.retryable = true;
    }

    /**
     * Creates exception with detailed stream information
     */
    public DataStreamException(String message, String streamName, String topicName,
                              String operation, boolean retryable) {
        super(String.format("%s (stream=%s, topic=%s, operation=%s, retryable=%s)",
            message, streamName, topicName, operation, retryable));
        this.streamName = streamName;
        this.topicName = topicName;
        this.operation = operation;
        this.retryable = retryable;
    }

    /**
     * Creates exception with detailed stream information and cause
     */
    public DataStreamException(String message, String streamName, String topicName,
                              String operation, boolean retryable, Throwable cause) {
        super(String.format("%s (stream=%s, topic=%s, operation=%s, retryable=%s)",
            message, streamName, topicName, operation, retryable), cause);
        this.streamName = streamName;
        this.topicName = topicName;
        this.operation = operation;
        this.retryable = retryable;
    }

    public String getStreamName() {
        return streamName;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getOperation() {
        return operation;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
