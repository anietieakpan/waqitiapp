package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result DTO for audit event recording
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventResult {
    
    // Audit record identification
    private UUID auditId;
    private String auditReference;
    private LocalDateTime timestamp;
    
    // Processing status
    private String status; // SUCCESS, PARTIAL_SUCCESS, FAILED
    private Boolean success;
    private String message;
    private List<String> warnings;
    private List<String> errors;
    
    // Integrity and security
    private String integrityHash;
    private String chainHash;
    private Boolean integrityVerified;
    private String signatureStatus;
    
    // Compliance and risk
    private String complianceStatus;
    private Double riskScore;
    private String riskLevel;
    private List<String> triggeredPolicies;
    private List<String> complianceViolations;
    
    // Suspicious activity detection
    private Boolean suspiciousActivityDetected;
    private List<String> suspiciousIndicators;
    private List<String> recommendedActions;
    
    // Notifications and alerts
    private List<String> notificationsSent;
    private List<String> alertsTriggered;
    private Map<String, String> notificationResults;
    
    // Related records
    private List<UUID> relatedAuditIds;
    private String correlationId;
    private String incidentId;
    
    // Performance metrics
    private Long processingTimeMs;
    private Long storageTimeMs;
    private Long notificationTimeMs;
    private Long totalTimeMs;
    
    // Storage and archival
    private String storageLocation;
    private Boolean archived;
    private LocalDateTime archiveDate;
    private Integer retentionDays;
    
    // Analytics and reporting
    private Map<String, Object> analyticsMetadata;
    private List<String> reportingTags;
    private Boolean includedInReporting;
    
    // Additional context
    private Map<String, Object> additionalInfo;
    private Map<String, String> debugInfo;
}