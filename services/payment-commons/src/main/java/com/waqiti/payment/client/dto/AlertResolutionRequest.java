package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Alert Resolution Request
 * 
 * Request to resolve or update the status of a fraud detection alert.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResolutionRequest {
    
    /**
     * Alert ID to resolve
     */
    private String alertId;
    
    /**
     * Resolution action
     */
    private ResolutionAction action;
    
    /**
     * Resolution status
     */
    private ResolutionStatus status;
    
    /**
     * Resolution reason/justification
     */
    private String reason;
    
    /**
     * Detailed notes about the resolution
     */
    private String notes;
    
    /**
     * User resolving the alert
     */
    private String resolvedBy;
    
    /**
     * Resolution timestamp
     */
    private LocalDateTime resolvedAt;
    
    /**
     * Whether this is a false positive
     */
    private Boolean falsePositive;
    
    /**
     * Fraud confirmed flag
     */
    private Boolean fraudConfirmed;
    
    /**
     * Risk level assessment
     */
    private String riskLevel;
    
    /**
     * Final risk score
     */
    private Double finalRiskScore;
    
    /**
     * Actions taken as result
     */
    private String actionsTaken;
    
    /**
     * Follow-up required
     */
    private Boolean followUpRequired;
    
    /**
     * Follow-up details
     */
    private String followUpDetails;
    
    /**
     * Follow-up due date
     */
    private LocalDateTime followUpDueDate;
    
    /**
     * Assigned to for follow-up
     */
    private String followUpAssignedTo;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Impact assessment
     */
    private ImpactAssessment impact;
    
    /**
     * Learning feedback for ML models
     */
    private LearningFeedback learningFeedback;
    
    /**
     * Quality assurance data
     */
    private QualityAssurance qualityAssurance;
    
    public enum ResolutionAction {
        APPROVE,
        DECLINE,
        REVIEW_MANUAL,
        ESCALATE,
        REQUEST_INFO,
        WHITELIST,
        BLACKLIST,
        CLOSE,
        INVESTIGATE,
        MONITOR
    }
    
    public enum ResolutionStatus {
        RESOLVED,
        PENDING,
        ESCALATED,
        REOPENED,
        CLOSED,
        CANCELLED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactAssessment {
        private String customerImpact;
        private String businessImpact;
        private String financialImpact;
        private String reputationalImpact;
        private Double impactScore;
        private String impactDescription;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningFeedback {
        private Boolean correctPrediction;
        private Double actualRiskScore;
        private String feedbackType;
        private String modelId;
        private Map<String, Object> feedbackData;
        private String improvementSuggestions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityAssurance {
        private String reviewLevel;
        private String reviewedBy;
        private LocalDateTime reviewedAt;
        private Boolean qualityPassed;
        private String qualityNotes;
        private Double qualityScore;
    }
    
    /**
     * Check if this is a false positive resolution
     */
    public boolean isFalsePositive() {
        return falsePositive != null && falsePositive;
    }
    
    /**
     * Check if fraud was confirmed
     */
    public boolean isFraudConfirmed() {
        return fraudConfirmed != null && fraudConfirmed;
    }
    
    /**
     * Check if follow-up is required
     */
    public boolean needsFollowUp() {
        return followUpRequired != null && followUpRequired;
    }
    
    /**
     * Check if resolution is complete
     */
    public boolean isComplete() {
        return status == ResolutionStatus.RESOLVED || 
               status == ResolutionStatus.CLOSED;
    }
    
    /**
     * Validate required fields
     */
    public boolean isValid() {
        return alertId != null && !alertId.trim().isEmpty() &&
               action != null &&
               status != null &&
               reason != null && !reason.trim().isEmpty() &&
               resolvedBy != null && !resolvedBy.trim().isEmpty();
    }
    
    /**
     * Get resolution summary
     */
    public String getResolutionSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(action.name());
        
        if (isFraudConfirmed()) {
            summary.append(" - Fraud Confirmed");
        } else if (isFalsePositive()) {
            summary.append(" - False Positive");
        }
        
        if (needsFollowUp()) {
            summary.append(" - Follow-up Required");
        }
        
        return summary.toString();
    }
}