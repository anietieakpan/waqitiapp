package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Velocity Limit Update
 * 
 * Request to update or modify velocity limit configurations.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityLimitUpdate {
    
    /**
     * Limit ID to update
     */
    private String limitId;
    
    /**
     * Update operation type
     */
    private UpdateOperation operation;
    
    /**
     * Updated limit name
     */
    private String limitName;
    
    /**
     * Updated description
     */
    private String description;
    
    /**
     * Updated limit value
     */
    private VelocityLimit.LimitValue limitValue;
    
    /**
     * Updated status
     */
    private VelocityLimit.LimitStatus status;
    
    /**
     * Updated priority
     */
    private VelocityLimit.Priority priority;
    
    /**
     * Updated enforcement action
     */
    private VelocityLimit.EnforcementAction enforcementAction;
    
    /**
     * Updated time window
     */
    private VelocityLimit.TimeWindow timeWindow;
    
    /**
     * Updated conditions
     */
    private VelocityLimit.LimitConditions conditions;
    
    /**
     * Updated effective from date
     */
    private LocalDateTime effectiveFrom;
    
    /**
     * Updated effective until date
     */
    private LocalDateTime effectiveUntil;
    
    /**
     * Updated metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Updated tags
     */
    private String tags;
    
    /**
     * Updated notes
     */
    private String notes;
    
    /**
     * Reason for the update
     */
    private String updateReason;
    
    /**
     * User making the update
     */
    private String updatedBy;
    
    /**
     * Update timestamp
     */
    private LocalDateTime updatedAt;
    
    /**
     * Whether to notify stakeholders
     */
    @Builder.Default
    private Boolean notifyStakeholders = false;
    
    /**
     * Approval required for this update
     */
    @Builder.Default
    private Boolean requiresApproval = false;
    
    /**
     * Approver details
     */
    private ApprovalInfo approvalInfo;
    
    /**
     * Rollback information
     */
    private RollbackInfo rollbackInfo;
    
    /**
     * Impact assessment
     */
    private ImpactAssessment impactAssessment;
    
    /**
     * Change tracking
     */
    private ChangeTracking changeTracking;
    
    public enum UpdateOperation {
        UPDATE,
        ACTIVATE,
        DEACTIVATE,
        SUSPEND,
        DELETE,
        CLONE,
        EXPIRE
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalInfo {
        private String approvedBy;
        private LocalDateTime approvedAt;
        private String approvalNotes;
        private Boolean approved;
        private String approvalWorkflowId;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackInfo {
        private Boolean canRollback;
        private String previousVersion;
        private LocalDateTime rollbackDeadline;
        private String rollbackReason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactAssessment {
        private String impactLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private Integer affectedUsers;
        private Integer affectedTransactions;
        private String businessImpact;
        private String technicalImpact;
        private java.util.List<String> affectedSystems;
        private String riskAssessment;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeTracking {
        private java.util.List<FieldChange> fieldChanges;
        private String changeType; // MINOR, MAJOR, CRITICAL
        private String changeCategory; // CONFIGURATION, POLICY, TECHNICAL
        private Boolean breakingChange;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldChange {
        private String fieldName;
        private Object oldValue;
        private Object newValue;
        private String changeReason;
        private String changeImpact;
    }
    
    /**
     * Check if this is a critical update
     */
    public boolean isCriticalUpdate() {
        return impactAssessment != null && 
               "CRITICAL".equals(impactAssessment.getImpactLevel()) ||
               (changeTracking != null && 
                ("CRITICAL".equals(changeTracking.getChangeType()) || 
                 changeTracking.getBreakingChange() != null && changeTracking.getBreakingChange()));
    }
    
    /**
     * Check if approval is needed
     */
    public boolean needsApproval() {
        return requiresApproval ||
               isCriticalUpdate() ||
               operation == UpdateOperation.DELETE ||
               (priority != null && 
                (priority == VelocityLimit.Priority.HIGH || priority == VelocityLimit.Priority.CRITICAL));
    }
    
    /**
     * Check if update is approved
     */
    public boolean isApproved() {
        return !needsApproval() || 
               (approvalInfo != null && 
                approvalInfo.getApproved() != null && 
                approvalInfo.getApproved());
    }
    
    /**
     * Check if update has high business impact
     */
    public boolean hasHighBusinessImpact() {
        return impactAssessment != null &&
               ("HIGH".equals(impactAssessment.getImpactLevel()) || 
                "CRITICAL".equals(impactAssessment.getImpactLevel()));
    }
    
    /**
     * Validate required fields
     */
    public boolean isValid() {
        if (limitId == null || limitId.trim().isEmpty()) {
            return false;
        }
        
        if (operation == null) {
            return false;
        }
        
        if (updatedBy == null || updatedBy.trim().isEmpty()) {
            return false;
        }
        
        // Specific validations based on operation
        switch (operation) {
            case UPDATE:
                return hasUpdatableFields();
            case DELETE:
                return updateReason != null && !updateReason.trim().isEmpty();
            case ACTIVATE:
            case DEACTIVATE:
            case SUSPEND:
            case EXPIRE:
                return true;
            case CLONE:
                return limitName != null && !limitName.trim().isEmpty();
            default:
                return false;
        }
    }
    
    /**
     * Check if any updatable fields are provided
     */
    private boolean hasUpdatableFields() {
        return limitName != null ||
               description != null ||
               limitValue != null ||
               status != null ||
               priority != null ||
               enforcementAction != null ||
               timeWindow != null ||
               conditions != null ||
               effectiveFrom != null ||
               effectiveUntil != null ||
               metadata != null ||
               tags != null ||
               notes != null;
    }
    
    /**
     * Get update summary
     */
    public String getUpdateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(operation.name());
        
        if (limitName != null) {
            summary.append(" - ").append(limitName);
        }
        
        if (updateReason != null) {
            summary.append(" (").append(updateReason).append(")");
        }
        
        return summary.toString();
    }
    
    /**
     * Check if rollback is possible
     */
    public boolean canRollback() {
        return rollbackInfo != null && 
               rollbackInfo.getCanRollback() != null && 
               rollbackInfo.getCanRollback() &&
               (rollbackInfo.getRollbackDeadline() == null || 
                LocalDateTime.now().isBefore(rollbackInfo.getRollbackDeadline()));
    }
    
    /**
     * Get affected systems count
     */
    public int getAffectedSystemsCount() {
        return impactAssessment != null && 
               impactAssessment.getAffectedSystems() != null ?
               impactAssessment.getAffectedSystems().size() : 0;
    }
    
    /**
     * Get number of field changes
     */
    public int getFieldChangesCount() {
        return changeTracking != null && 
               changeTracking.getFieldChanges() != null ?
               changeTracking.getFieldChanges().size() : 0;
    }
    
    /**
     * Check if this is an emergency update
     */
    public boolean isEmergencyUpdate() {
        return priority == VelocityLimit.Priority.CRITICAL ||
               "CRITICAL".equals(impactAssessment != null ? impactAssessment.getImpactLevel() : null) ||
               operation == UpdateOperation.SUSPEND ||
               operation == UpdateOperation.DEACTIVATE;
    }
}