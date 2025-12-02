package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blacklist request DTO
 * Request for adding, updating, or managing blacklist entries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BlacklistRequest {
    
    @NotNull
    private UUID requestId;
    
    @NotNull
    private RequestType requestType;
    
    private UUID entryId; // For UPDATE/DELETE operations
    
    // Entry information
    @NotNull
    @Size(min = 1, max = 500)
    private String value;
    
    @NotNull
    private BlacklistEntry.EntryType entryType;
    
    @NotNull
    private String blacklistName;
    
    @NotNull
    private BlacklistEntry.BlacklistCategory category;
    
    @NotNull
    private BlacklistEntry.RiskLevel riskLevel;
    
    // Entry details
    @NotNull
    @Size(min = 1, max = 1000)
    private String reason;
    
    @Size(max = 2000)
    private String description;
    
    // Validity period
    private LocalDateTime validFrom;
    
    private LocalDateTime validUntil;
    
    // Match configuration
    private BlacklistEntry.MatchConfiguration matchConfig;
    
    // Request context
    private RequestContext requestContext;
    
    // Approval and workflow
    private ApprovalRequest approvalRequest;
    
    // Batch operation support
    @Builder.Default
    private List<BatchEntryRequest> batchEntries = List.of();
    
    // Additional metadata
    @Builder.Default
    private List<String> tags = List.of();
    
    private String jurisdiction;
    
    private String regulatorySource;
    
    private String caseNumber;
    
    private UUID investigationId;
    
    private Map<String, Object> metadata;
    
    // Request tracking
    private String requestedBy;
    
    private LocalDateTime requestedAt;
    
    public enum RequestType {
        ADD,
        UPDATE,
        REMOVE,
        ACTIVATE,
        DEACTIVATE,
        EXTEND_VALIDITY,
        BATCH_ADD,
        BATCH_UPDATE,
        BATCH_REMOVE
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestContext {
        private String businessJustification;
        private String impactAssessment;
        private String riskMitigation;
        private String complianceRequirement;
        private String sourceOfInformation;
        private Integer urgencyLevel; // 1-5
        private String relatedIncident;
        private Map<String, Object> additionalContext;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalRequest {
        @Builder.Default
        private Boolean requiresApproval = true;
        
        private String approverRole;
        
        private String approverUserId;
        
        private String approvalReason;
        
        private String businessImpact;
        
        private Integer urgencyLevel; // 1-5
        
        private LocalDateTime requestedApprovalDate;
        
        private String escalationPath;
        
        private Map<String, Object> approvalMetadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchEntryRequest {
        @NotNull
        private String value;
        
        @NotNull
        private BlacklistEntry.EntryType entryType;
        
        @NotNull
        private String reason;
        
        private String description;
        
        private BlacklistEntry.RiskLevel riskLevel;
        
        private LocalDateTime validFrom;
        
        private LocalDateTime validUntil;
        
        private BlacklistEntry.MatchConfiguration matchConfig;
        
        @Builder.Default
        private List<String> tags = List.of();
        
        private Map<String, Object> entryMetadata;
    }
    
    // Business logic methods
    public boolean isBatchRequest() {
        return requestType == RequestType.BATCH_ADD ||
               requestType == RequestType.BATCH_UPDATE ||
               requestType == RequestType.BATCH_REMOVE;
    }
    
    public boolean isHighRisk() {
        return riskLevel == BlacklistEntry.RiskLevel.HIGH ||
               riskLevel == BlacklistEntry.RiskLevel.CRITICAL;
    }
    
    public boolean requiresApproval() {
        return approvalRequest != null && 
               approvalRequest.getRequiresApproval() != null && 
               approvalRequest.getRequiresApproval();
    }
    
    public boolean isUrgent() {
        return (requestContext != null && 
                requestContext.getUrgencyLevel() != null && 
                requestContext.getUrgencyLevel() >= 4) ||
               (approvalRequest != null && 
                approvalRequest.getUrgencyLevel() != null && 
                approvalRequest.getUrgencyLevel() >= 4);
    }
    
    public boolean isRegulatoryRelated() {
        return category == BlacklistEntry.BlacklistCategory.SANCTIONS ||
               category == BlacklistEntry.BlacklistCategory.AML ||
               category == BlacklistEntry.BlacklistCategory.TERRORISM ||
               category == BlacklistEntry.BlacklistCategory.PEP ||
               category == BlacklistEntry.BlacklistCategory.REGULATORY ||
               regulatorySource != null;
    }
    
    public boolean isTemporary() {
        return validUntil != null ||
               category == BlacklistEntry.BlacklistCategory.TEMPORARY_BLOCK;
    }
    
    public boolean isUpdateOperation() {
        return requestType == RequestType.UPDATE ||
               requestType == RequestType.EXTEND_VALIDITY ||
               requestType == RequestType.BATCH_UPDATE;
    }
    
    public boolean isRemovalOperation() {
        return requestType == RequestType.REMOVE ||
               requestType == RequestType.DEACTIVATE ||
               requestType == RequestType.BATCH_REMOVE;
    }
    
    public Integer getEffectiveUrgencyLevel() {
        Integer contextUrgency = requestContext != null ? requestContext.getUrgencyLevel() : null;
        Integer approvalUrgency = approvalRequest != null ? approvalRequest.getUrgencyLevel() : null;
        
        if (contextUrgency != null && approvalUrgency != null) {
            return Math.max(contextUrgency, approvalUrgency);
        }
        
        return contextUrgency != null ? contextUrgency : 
               (approvalUrgency != null ? approvalUrgency : 3); // Default medium priority
    }
    
    public Integer getBatchSize() {
        return isBatchRequest() ? batchEntries.size() : 1;
    }
    
    public boolean hasValidityPeriod() {
        return validFrom != null || validUntil != null;
    }
    
    public boolean hasCustomMatchConfig() {
        return matchConfig != null && 
               matchConfig.getMatchType() != BlacklistEntry.MatchConfiguration.MatchType.EXACT;
    }
    
    public String getPrimaryJustification() {
        return requestContext != null ? requestContext.getBusinessJustification() : reason;
    }
    
    public boolean requiresImmediateProcessing() {
        return isUrgent() && 
               (isRegulatoryRelated() || 
                riskLevel == BlacklistEntry.RiskLevel.CRITICAL);
    }
    
    public boolean hasInvestigationContext() {
        return investigationId != null || 
               caseNumber != null ||
               (requestContext != null && requestContext.getRelatedIncident() != null);
    }
}