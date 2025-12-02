package com.waqiti.common.audit.dto;

import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;
import com.waqiti.common.audit.domain.AuditLog.OperationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for audit log search operations
 * 
 * Supports complex search criteria with multiple filters for comprehensive
 * audit log retrieval and compliance reporting.
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditSearchRequest {
    
    // User and session filters
    private String userId;
    private String username;
    private String sessionId;
    private String correlationId;
    
    // Event filters
    private List<AuditEventType> eventTypes;
    private List<EventCategory> eventCategories;
    private List<Severity> severityLevels;
    private List<OperationResult> operationResults;
    
    // Entity filters
    private String entityType;
    private String entityId;
    
    // Time range filters
    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;
    
    @NotNull(message = "End date is required")
    private LocalDateTime endDate;
    
    // Network and device filters
    private String ipAddress;
    private String userAgent;
    private String deviceId;
    
    // Location filters
    private String locationCountry;
    private String locationRegion;
    private String locationCity;
    
    // Content filters
    private String actionContains;
    private String descriptionContains;
    private String metadataContains;
    
    // Risk and compliance filters
    private Integer minRiskScore;
    private Integer maxRiskScore;
    private Boolean pciRelevant;
    private Boolean gdprRelevant;
    private Boolean soxRelevant;
    private Boolean soc2Relevant;
    private Boolean requiresNotification;
    private Boolean investigationRequired;
    
    // Result filters
    private Boolean successfulOnly;
    private Boolean failedOnly;
    private String errorCodeContains;
    private String failureReasonContains;
    
    // Advanced filters
    private Boolean hasMetadata;
    private Boolean hasFraudIndicators;
    private Boolean isArchived;
    private String retentionPolicy;
    
    // Text search
    private String searchText;
    private Boolean useFullTextSearch;
    
    // Export options
    private Boolean includeMetadata;
    private Boolean includeSensitiveData;
    private List<String> fieldsToInclude;
    private List<String> fieldsToExclude;
    
    // Aggregation options
    private Boolean includeStatistics;
    private List<String> groupByFields;
    
    /**
     * Validate the search request
     */
    public boolean isValid() {
        if (startDate == null || endDate == null) {
            return false;
        }
        
        if (startDate.isAfter(endDate)) {
            return false;
        }
        
        // Validate date range is not too large (max 1 year for performance)
        if (startDate.isBefore(endDate.minusYears(1))) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if this is a compliance-specific search
     */
    public boolean isComplianceSearch() {
        return Boolean.TRUE.equals(pciRelevant) ||
               Boolean.TRUE.equals(gdprRelevant) ||
               Boolean.TRUE.equals(soxRelevant) ||
               Boolean.TRUE.equals(soc2Relevant);
    }
    
    /**
     * Check if this is a security investigation search
     */
    public boolean isSecurityInvestigation() {
        return Boolean.TRUE.equals(investigationRequired) ||
               (minRiskScore != null && minRiskScore > 50) ||
               Boolean.TRUE.equals(hasFraudIndicators) ||
               (eventCategories != null && eventCategories.contains(EventCategory.SECURITY));
    }
    
    /**
     * Check if sensitive data should be included
     */
    public boolean shouldIncludeSensitiveData() {
        return Boolean.TRUE.equals(includeSensitiveData);
    }
    
    /**
     * Get effective fields to include
     */
    public List<String> getEffectiveFieldsToInclude() {
        if (fieldsToInclude != null && !fieldsToInclude.isEmpty()) {
            return fieldsToInclude;
        }
        
        // Default fields for different search types
        if (isComplianceSearch()) {
            return List.of("id", "timestamp", "eventType", "eventCategory", "severity", 
                          "userId", "action", "description", "result", "complianceFlags");
        } else if (isSecurityInvestigation()) {
            return List.of("id", "timestamp", "eventType", "severity", "userId", "ipAddress", 
                          "userAgent", "action", "result", "riskScore", "fraudIndicators");
        } else {
            return List.of("id", "timestamp", "eventType", "eventCategory", "userId", 
                          "action", "description", "result");
        }
    }
    
    /**
     * Get effective fields to exclude
     */
    public List<String> getEffectiveFieldsToExclude() {
        if (fieldsToExclude != null && !fieldsToExclude.isEmpty()) {
            return fieldsToExclude;
        }
        
        // Default exclusions if sensitive data not requested
        if (!shouldIncludeSensitiveData()) {
            return List.of("metadata", "userAgent", "deviceFingerprint", "signature", "hash");
        }
        
        return List.of();
    }
}