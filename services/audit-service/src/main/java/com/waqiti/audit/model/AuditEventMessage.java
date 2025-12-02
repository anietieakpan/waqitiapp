package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Kafka message for audit event publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEventMessage {
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("event_type")
    private String eventType;
    
    @JsonProperty("entity_type")
    private String entityType;
    
    @JsonProperty("entity_id")
    private String entityId;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("severity")
    private String severity;
    
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private Instant timestamp;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("service_name")
    private String serviceName;
}