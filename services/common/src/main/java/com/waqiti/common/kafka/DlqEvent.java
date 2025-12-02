package com.waqiti.common.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.common.header.Headers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dead Letter Queue Event structure
 * Represents an event that failed processing and was sent to DLQ
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DlqEvent {

    @JsonProperty("dlqEventId")
    @Builder.Default
    private String dlqEventId = UUID.randomUUID().toString();

    @JsonProperty("originalEventId")
    private String originalEventId;

    @JsonProperty("originalTopic")
    private String originalTopic;

    @JsonProperty("dlqTopic")
    private String dlqTopic;

    @JsonProperty("consumerGroup")
    private String consumerGroup;

    @JsonProperty("partition")
    private Integer partition;

    @JsonProperty("offset")
    private Long offset;

    @JsonProperty("key")
    private String key;

    @JsonProperty("value")
    private Object value;

    @JsonProperty("headers")
    private Map<String, String> headers;

    @JsonProperty("errorType")
    private String errorType;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("stackTrace")
    private String stackTrace;

    @JsonProperty("timestamp")
    @Builder.Default
    private Long timestamp = Instant.now().toEpochMilli();

    @JsonProperty("retryCount")
    @Builder.Default
    private Integer retryCount = 0;

    @JsonProperty("maxRetries")
    @Builder.Default
    private Integer maxRetries = 3;

    @JsonProperty("nextRetryAt")
    private Long nextRetryAt;

    @JsonProperty("dlqStatus")
    @Builder.Default
    private DlqStatus dlqStatus = DlqStatus.PENDING;

    @JsonProperty("resolutionStrategy")
    private String resolutionStrategy;

    @JsonProperty("processingContext")
    private Map<String, Object> processingContext;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * DLQ Status enumeration
     */
    public enum DlqStatus {
        PENDING,
        RETRYING,
        RESOLVED,
        ABANDONED,
        MANUAL_REVIEW_REQUIRED
    }

    /**
     * Set headers from Kafka Headers object
     */
    public void setHeaders(Headers kafkaHeaders) {
        if (kafkaHeaders != null) {
            this.headers = new HashMap<>();
            kafkaHeaders.forEach(header -> 
                this.headers.put(header.key(), new String(header.value())));
        }
    }

    /**
     * Check if retry is possible
     */
    public boolean canRetry() {
        return retryCount < maxRetries && 
               dlqStatus == DlqStatus.PENDING && 
               (nextRetryAt == null || Instant.now().toEpochMilli() >= nextRetryAt);
    }

    /**
     * Increment retry count and set next retry time
     */
    public void incrementRetry(long retryDelayMs) {
        this.retryCount++;
        this.nextRetryAt = Instant.now().toEpochMilli() + retryDelayMs;
        
        if (this.retryCount >= this.maxRetries) {
            this.dlqStatus = DlqStatus.MANUAL_REVIEW_REQUIRED;
        } else {
            this.dlqStatus = DlqStatus.RETRYING;
        }
    }

    /**
     * Mark as resolved
     */
    public void markResolved(String strategy) {
        this.dlqStatus = DlqStatus.RESOLVED;
        this.resolutionStrategy = strategy;
        addMetadata("resolvedAt", Instant.now().toEpochMilli());
    }

    /**
     * Mark as abandoned
     */
    public void markAbandoned(String reason) {
        this.dlqStatus = DlqStatus.ABANDONED;
        this.resolutionStrategy = "ABANDONED";
        addMetadata("abandonedAt", Instant.now().toEpochMilli());
        addMetadata("abandonReason", reason);
    }

    /**
     * Add metadata
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get metadata value
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        
        return null;
    }

    /**
     * Add processing context
     */
    public void addProcessingContext(String key, Object value) {
        if (this.processingContext == null) {
            this.processingContext = new HashMap<>();
        }
        this.processingContext.put(key, value);
    }

    /**
     * Get processing context value
     */
    @SuppressWarnings("unchecked")
    public <T> T getProcessingContext(String key, Class<T> type) {
        if (processingContext == null || !processingContext.containsKey(key)) {
            return null;
        }
        
        Object value = processingContext.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        
        return null;
    }

    /**
     * Check if event is in terminal state
     */
    public boolean isTerminal() {
        return dlqStatus == DlqStatus.RESOLVED || 
               dlqStatus == DlqStatus.ABANDONED;
    }

    /**
     * Check if manual intervention is required
     */
    public boolean requiresManualIntervention() {
        return dlqStatus == DlqStatus.MANUAL_REVIEW_REQUIRED ||
               retryCount >= maxRetries;
    }

    /**
     * Get age of DLQ event in milliseconds
     */
    public long getAgeMs() {
        return Instant.now().toEpochMilli() - timestamp;
    }

    /**
     * Check if DLQ event is stale
     */
    public boolean isStale(long stalenessThresholdMs) {
        return getAgeMs() > stalenessThresholdMs;
    }

    /**
     * Get retry delay based on exponential backoff
     */
    public long calculateRetryDelay(long baseDelayMs, double backoffMultiplier) {
        return (long) (baseDelayMs * Math.pow(backoffMultiplier, retryCount));
    }

    /**
     * Create a summary for logging
     */
    public String getSummary() {
        return String.format("DLQ Event [ID: %s, Original: %s, Topic: %s -> %s, Status: %s, Retries: %d/%d]",
                           dlqEventId, originalEventId, originalTopic, dlqTopic, 
                           dlqStatus, retryCount, maxRetries);
    }

    /**
     * Validate required fields
     */
    public boolean isValid() {
        return dlqEventId != null && !dlqEventId.trim().isEmpty() &&
               originalTopic != null && !originalTopic.trim().isEmpty() &&
               dlqTopic != null && !dlqTopic.trim().isEmpty() &&
               errorMessage != null && !errorMessage.trim().isEmpty() &&
               timestamp != null;
    }

    @Override
    public String toString() {
        return getSummary();
    }
}