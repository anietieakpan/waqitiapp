package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Risk score update request DTO
 * For updating user/entity risk scores with new information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RiskScoreUpdate {
    
    @NotNull
    private UUID updateId;
    
    @NotNull
    private String entityId; // User ID, Merchant ID, etc.
    
    @NotNull
    private RiskScoreRequest.EntityType entityType;
    
    @NotNull
    private UpdateTrigger updateTrigger;
    
    @NotNull
    private LocalDateTime updateTimestamp;
    
    // New risk score
    private BigDecimal newRiskScore;
    
    // Previous risk score for comparison
    private BigDecimal previousRiskScore;
    
    // Score change details
    private ScoreChangeDetails scoreChange;
    
    // Reason for the update
    private UpdateReason updateReason;
    
    // Supporting evidence
    private UpdateEvidence evidence;
    
    // Update context
    private UpdateContext context;
    
    // Validation and approval
    private UpdateValidation validation;
    
    // Additional metadata
    private Map<String, Object> updateMetadata;
    
    public enum UpdateTrigger {
        TRANSACTION_BASED,     // Triggered by transaction activity
        BEHAVIORAL_CHANGE,     // Behavioral pattern changes
        EXTERNAL_DATA,         // New external data received
        MANUAL_REVIEW,         // Manual analyst review
        SCHEDULED_REFRESH,     // Scheduled recalculation
        COMPLIANCE_EVENT,      // Compliance-related event
        FRAUD_DETECTION,       // Fraud detection triggered
        MODEL_REFRESH,         // ML model update
        DATA_CORRECTION,       // Data quality correction
        SYSTEM_MIGRATION       // System/data migration
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreChangeDetails {
        private BigDecimal scoreDelta;
        private BigDecimal percentageChange;
        private ScoreMovement movement;
        private SignificanceLevel significance;
        private ChangeVelocity velocity;
        private String changeExplanation;
        
        public enum ScoreMovement {
            SIGNIFICANT_INCREASE,
            MODERATE_INCREASE,
            MINIMAL_INCREASE,
            NO_CHANGE,
            MINIMAL_DECREASE,
            MODERATE_DECREASE,
            SIGNIFICANT_DECREASE
        }
        
        public enum SignificanceLevel {
            CRITICAL,
            HIGH,
            MEDIUM,
            LOW,
            MINIMAL
        }
        
        public enum ChangeVelocity {
            RAPID,      // Change within hours
            FAST,       // Change within days
            MODERATE,   // Change within weeks
            GRADUAL,    // Change over months
            STABLE      // Minimal change over time
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateReason {
        private String primaryReason;
        private String detailedExplanation;
        private String businessJustification;
        private String riskImpact;
        private Map<String, Object> supportingFactors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateEvidence {
        private String transactionId;
        private String eventId;
        private String dataSource;
        private LocalDateTime evidenceTimestamp;
        private EvidenceType evidenceType;
        private String evidenceDescription;
        private Map<String, Object> evidenceData;
        private Double evidenceConfidence;
        
        public enum EvidenceType {
            TRANSACTION_DATA,
            BEHAVIORAL_DATA,
            EXTERNAL_VERIFICATION,
            COMPLIANCE_CHECK,
            FRAUD_INDICATOR,
            USER_ACTION,
            SYSTEM_EVENT,
            ANALYST_INPUT
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateContext {
        private String initiatedBy;
        private String businessUnit;
        private String applicationContext;
        private String geographicContext;
        private String regulatoryContext;
        private String operationalContext;
        private Map<String, Object> contextualAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateValidation {
        private ValidationStatus validationStatus;
        private String validatedBy;
        private LocalDateTime validatedAt;
        private String validationNotes;
        private Boolean requiresApproval;
        private Boolean isApproved;
        private String approvedBy;
        private LocalDateTime approvedAt;
        private String approvalNotes;
        
        public enum ValidationStatus {
            PENDING,
            VALIDATED,
            REJECTED,
            REQUIRES_REVIEW,
            AUTO_APPROVED
        }
    }
    
    // Business logic methods
    public boolean isSignificantChange() {
        return scoreChange != null && 
               (scoreChange.getSignificance() == ScoreChangeDetails.SignificanceLevel.HIGH ||
                scoreChange.getSignificance() == ScoreChangeDetails.SignificanceLevel.CRITICAL);
    }
    
    public boolean isRapidChange() {
        return scoreChange != null && 
               (scoreChange.getVelocity() == ScoreChangeDetails.ChangeVelocity.RAPID ||
                scoreChange.getVelocity() == ScoreChangeDetails.ChangeVelocity.FAST);
    }
    
    public boolean requiresManualReview() {
        return isSignificantChange() || 
               isRapidChange() ||
               updateTrigger == UpdateTrigger.FRAUD_DETECTION ||
               (validation != null && validation.getRequiresApproval() != null && validation.getRequiresApproval());
    }
    
    public boolean isScoreIncreasing() {
        return scoreChange != null && 
               newRiskScore != null && 
               previousRiskScore != null &&
               newRiskScore.compareTo(previousRiskScore) > 0;
    }
    
    public boolean isValidated() {
        return validation != null && 
               validation.getValidationStatus() == UpdateValidation.ValidationStatus.VALIDATED;
    }
    
    public boolean isApproved() {
        return validation != null && 
               validation.getIsApproved() != null && 
               validation.getIsApproved();
    }
    
    public BigDecimal getScoreChangePercentage() {
        if (scoreChange != null && scoreChange.getPercentageChange() != null) {
            return scoreChange.getPercentageChange();
        }
        
        if (previousRiskScore != null && 
            newRiskScore != null && 
            previousRiskScore.compareTo(BigDecimal.ZERO) != 0) {
            
            BigDecimal delta = newRiskScore.subtract(previousRiskScore);
            return delta.divide(previousRiskScore, 4, java.math.RoundingMode.HALF_UP)
                      .multiply(new BigDecimal("100"));
        }
        
        return BigDecimal.ZERO;
    }
    
    public boolean isComplianceRelated() {
        return updateTrigger == UpdateTrigger.COMPLIANCE_EVENT ||
               (evidence != null && 
                evidence.getEvidenceType() == UpdateEvidence.EvidenceType.COMPLIANCE_CHECK);
    }
    
    public boolean isFraudRelated() {
        return updateTrigger == UpdateTrigger.FRAUD_DETECTION ||
               (evidence != null && 
                evidence.getEvidenceType() == UpdateEvidence.EvidenceType.FRAUD_INDICATOR);
    }
}