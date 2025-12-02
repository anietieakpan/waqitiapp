package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Detailed audit event response with full information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEventDetailResponse {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("event_type")
    private String eventType;
    
    @JsonProperty("service_name")
    private String serviceName;
    
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private Instant timestamp;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("session_id")
    private String sessionId;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("transaction_id")
    private String transactionId;
    
    @JsonProperty("resource_id")
    private String resourceId;
    
    @JsonProperty("resource_type")
    private String resourceType;
    
    @JsonProperty("action")
    private String action;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("result")
    private AuditEvent.AuditResult result;
    
    @JsonProperty("ip_address")
    private String ipAddress;
    
    @JsonProperty("user_agent")
    private String userAgent;
    
    @JsonProperty("duration_ms")
    private Long durationMs;
    
    @JsonProperty("severity")
    private AuditSeverity severity;
    
    @JsonProperty("compliance_tags")
    private String complianceTags;
    
    @JsonProperty("risk_score")
    private Integer riskScore;
    
    @JsonProperty("data_classification")
    private String dataClassification;
    
    @JsonProperty("metadata")
    private Map<String, String> metadata;
    
    @JsonProperty("tags")
    private Set<String> tags;
    
    @JsonProperty("before_state")
    private String beforeState;
    
    @JsonProperty("after_state")
    private String afterState;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    @JsonProperty("integrity_hash")
    private String integrityHash;
    
    @JsonProperty("previous_event_hash")
    private String previousEventHash;
    
    @JsonProperty("digital_signature")
    private String digitalSignature;
    
    @JsonProperty("signing_key_id")
    private String signingKeyId;
    
    @JsonProperty("geographical_region")
    private String geographicalRegion;
    
    @JsonProperty("regulatory_jurisdiction")
    private String regulatoryJurisdiction;
    
    @JsonProperty("retention_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private Instant retentionDate;
    
    @JsonProperty("archived")
    private Boolean archived;
    
    @JsonProperty("event_version")
    private String eventVersion;
    
    @JsonProperty("related_events")
    private List<RelatedEventInfo> relatedEvents;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RelatedEventInfo {
        
        @JsonProperty("event_id")
        private String eventId;
        
        @JsonProperty("relationship_type")
        private String relationshipType;
        
        @JsonProperty("timestamp")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        private Instant timestamp;
    }
}