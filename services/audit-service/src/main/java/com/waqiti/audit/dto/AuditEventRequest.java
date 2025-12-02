package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for recording audit events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventRequest {
    
    // Core event information
    private String eventType;
    private String entityType;
    private String entityId;
    private String action;
    
    // User and session context
    private String userId;
    private String userName;
    private String sessionId;
    private String sourceIpAddress;
    private String userAgent;
    private String deviceId;
    
    // State tracking
    private String beforeState;
    private String afterState;
    private String changeDetails;
    private Map<String, Object> changedFields;
    
    // Business context
    private String businessContext;
    private String businessProcessId;
    private String transactionId;
    private String correlationId;
    
    // Risk and compliance
    private String riskLevel;
    private List<String> complianceFlags;
    private Map<String, String> regulatoryTags;
    
    // Service information
    private String serviceOrigin;
    private String serviceName;
    private String serviceVersion;
    private String apiEndpoint;
    private String httpMethod;
    
    // Additional metadata
    private Map<String, String> metadata;
    private Map<String, Object> customAttributes;
    
    // Timing information
    private LocalDateTime eventTimestamp;
    private Long processingTimeMs;
    
    // Security context
    private List<String> userRoles;
    private List<String> userPermissions;
    private String authenticationMethod;
    private Boolean mfaUsed;
    
    // Data classification
    private String dataClassification;
    private Boolean containsPii;
    private Boolean containsSensitiveData;
    
    // Audit configuration
    private Boolean forceSync;
    private Integer retentionDays;
    private Boolean requireIntegrityCheck;
    private Boolean skipNotification;
}