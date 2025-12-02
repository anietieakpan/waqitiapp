package com.waqiti.common.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Generic Kafka event structure for all event types
 * Provides a consistent base structure for event processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericKafkaEvent {

    @JsonProperty("eventId")
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("eventVersion")
    @Builder.Default
    private String eventVersion = "1.0";

    @JsonProperty("timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @JsonProperty("source")
    private String source;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("causationId")
    private String causationId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("spanId")
    private String spanId;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("tags")
    private Map<String, String> tags;

    /**
     * Get a payload value with type casting
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadValue(String key, Class<T> type) {
        if (payload == null || !payload.containsKey(key)) {
            return null;
        }
        
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        
        if (type.isInstance(value)) {
            return (T) value;
        }
        
        // Basic type conversions
        if (type == String.class) {
            return (T) value.toString();
        }
        
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        
        if (type == Double.class && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        }
        
        if (type == Boolean.class) {
            if (value instanceof Boolean) {
                return (T) value;
            }
            if (value instanceof String) {
                return (T) Boolean.valueOf((String) value);
            }
        }
        
        return null;
    }

    /**
     * Set a payload value
     */
    public void setPayloadValue(String key, Object value) {
        if (payload == null) {
            payload = new java.util.HashMap<>();
        }
        payload.put(key, value);
    }

    /**
     * Get metadata value
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key, Class<T> type) {
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
     * Set metadata value
     */
    public void setMetadataValue(String key, Object value) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * Get tag value
     */
    public String getTag(String key) {
        return tags != null ? tags.get(key) : null;
    }

    /**
     * Set tag value
     */
    public void setTag(String key, String value) {
        if (tags == null) {
            tags = new java.util.HashMap<>();
        }
        tags.put(key, value);
    }

    /**
     * Check if this is a specific event type
     */
    public boolean isEventType(String expectedType) {
        return expectedType != null && expectedType.equals(this.eventType);
    }

    /**
     * Check if event has correlation ID
     */
    public boolean hasCorrelationId() {
        return correlationId != null && !correlationId.trim().isEmpty();
    }

    /**
     * Check if event has user context
     */
    public boolean hasUserContext() {
        return userId != null && !userId.trim().isEmpty();
    }

    /**
     * Create a derived event with same correlation context
     */
    public GenericKafkaEvent createDerivedEvent(String newEventType, Map<String, Object> newPayload) {
        return GenericKafkaEvent.builder()
            .eventType(newEventType)
            .eventVersion(this.eventVersion)
            .source(this.source)
            .correlationId(this.correlationId)
            .causationId(this.eventId) // Current event becomes causation
            .userId(this.userId)
            .sessionId(this.sessionId)
            .traceId(this.traceId)
            .spanId(UUID.randomUUID().toString()) // New span
            .payload(newPayload)
            .metadata(this.metadata != null ? new java.util.HashMap<>(this.metadata) : null)
            .tags(this.tags != null ? new java.util.HashMap<>(this.tags) : null)
            .build();
    }

    /**
     * Validate required fields
     */
    public boolean isValid() {
        return eventId != null && !eventId.trim().isEmpty() &&
               eventType != null && !eventType.trim().isEmpty() &&
               timestamp != null &&
               source != null && !source.trim().isEmpty();
    }

    /**
     * Get event age in milliseconds
     */
    public long getAgeMs() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().toEpochMilli() - timestamp.toEpochMilli();
    }

    /**
     * Check if event is stale (older than threshold)
     */
    public boolean isStale(long thresholdMs) {
        return getAgeMs() > thresholdMs;
    }

    @Override
    public String toString() {
        return String.format("GenericKafkaEvent{eventId='%s', eventType='%s', source='%s', timestamp=%s}", 
                           eventId, eventType, source, timestamp);
    }
}