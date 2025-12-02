package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Search criteria for audit events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditSearchRequest {
    
    @JsonProperty("entity_type")
    @Size(max = 50)
    private String entityType;
    
    @JsonProperty("entity_id")
    @Size(max = 100)
    private String entityId;
    
    @JsonProperty("event_type")
    @Size(max = 100)
    private String eventType;
    
    @JsonProperty("user_id")
    @Size(max = 50)
    private String userId;
    
    @JsonProperty("start_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startDate;
    
    @JsonProperty("end_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endDate;
    
    @JsonProperty("severity")
    private AuditSeverity severity;
    
    @JsonProperty("action")
    @Size(max = 100)
    private String action;
    
    @JsonProperty("service_name")
    @Size(max = 100)
    private String serviceName;
    
    @JsonProperty("correlation_id")
    @Size(max = 100)
    private String correlationId;
    
    @JsonProperty("transaction_id")
    @Size(max = 100)
    private String transactionId;
}