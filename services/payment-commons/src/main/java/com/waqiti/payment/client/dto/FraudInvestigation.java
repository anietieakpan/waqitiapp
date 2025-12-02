package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fraud Investigation
 * 
 * Represents a fraud investigation case with all related data and analysis.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudInvestigation {
    
    /**
     * Investigation ID
     */
    private String investigationId;
    
    /**
     * Investigation case number
     */
    private String caseNumber;
    
    /**
     * Investigation status
     */
    private InvestigationStatus status;
    
    /**
     * Investigation priority
     */
    private Priority priority;
    
    /**
     * Investigation type
     */
    private InvestigationType type;
    
    /**
     * Subject of investigation
     */
    private InvestigationSubject subject;
    
    /**
     * Related transaction IDs
     */
    private List<String> transactionIds;
    
    /**
     * Related user IDs
     */
    private List<String> userIds;
    
    /**
     * Total amount involved
     */
    private BigDecimal totalAmount;
    
    /**
     * Currency
     */
    private String currency;
    
    /**
     * Investigation title
     */
    private String title;
    
    /**
     * Investigation description
     */
    private String description;
    
    /**
     * Initial alert that triggered investigation
     */
    private String triggerAlertId;
    
    /**
     * Investigation findings
     */
    private List<Finding> findings;
    
    /**
     * Evidence collected
     */
    private List<Evidence> evidence;
    
    /**
     * Investigation timeline
     */
    private List<TimelineEvent> timeline;
    
    /**
     * Risk assessment
     */
    private RiskAssessment riskAssessment;
    
    /**
     * Actions taken
     */
    private List<InvestigationAction> actions;
    
    /**
     * Assigned investigator
     */
    private String assignedTo;
    
    /**
     * Investigation team
     */
    private List<String> teamMembers;
    
    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;
    
    /**
     * Due date for investigation
     */
    private LocalDateTime dueDate;
    
    /**
     * Completed timestamp
     */
    private LocalDateTime completedAt;
    
    /**
     * Investigation outcome
     */
    private InvestigationOutcome outcome;
    
    /**
     * Resolution details
     */
    private String resolution;
    
    /**
     * Follow-up actions required
     */
    private List<String> followUpActions;
    
    /**
     * External case references
     */
    private List<String> externalReferences;
    
    /**
     * Investigation notes
     */
    private String notes;
    
    /**
     * Tags for categorization
     */
    private List<String> tags;
    
    /**
     * Metadata
     */
    private Map<String, Object> metadata;
    
    public enum InvestigationStatus {
        OPEN,
        IN_PROGRESS,
        PENDING_REVIEW,
        ESCALATED,
        CLOSED,
        CANCELLED,
        ON_HOLD
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL,
        URGENT
    }
    
    public enum InvestigationType {
        FRAUD_ALERT,
        SUSPICIOUS_ACTIVITY,
        CHARGEBACKS,
        ACCOUNT_TAKEOVER,
        IDENTITY_THEFT,
        MONEY_LAUNDERING,
        MERCHANT_FRAUD,
        INTERNAL_FRAUD,
        COMPLIANCE_VIOLATION,
        CUSTOMER_COMPLAINT
    }
    
    public enum InvestigationOutcome {
        FRAUD_CONFIRMED,
        NO_FRAUD_DETECTED,
        INCONCLUSIVE,
        FALSE_POSITIVE,
        POLICY_VIOLATION,
        SYSTEM_ERROR,
        CUSTOMER_ERROR
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestigationSubject {
        private String subjectType; // USER, MERCHANT, TRANSACTION, DEVICE, etc.
        private String subjectId;
        private String subjectName;
        private Map<String, Object> subjectDetails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finding {
        private String findingId;
        private String findingType;
        private String description;
        private String severity;
        private Double confidence;
        private LocalDateTime discoveredAt;
        private String discoveredBy;
        private Map<String, Object> details;
        private List<String> supportingEvidence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private String evidenceId;
        private String evidenceType;
        private String description;
        private String sourceSystem;
        private String dataLocation;
        private LocalDateTime collectedAt;
        private String collectedBy;
        private Boolean verified;
        private Map<String, Object> evidenceData;
        private String hash; // For integrity verification
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private LocalDateTime timestamp;
        private String eventType;
        private String description;
        private String actor;
        private Map<String, Object> eventData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private Double overallRiskScore;
        private String riskLevel;
        private List<RiskFactor> riskFactors;
        private String assessmentMethod;
        private LocalDateTime assessedAt;
        private String assessedBy;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorName;
        private String factorType;
        private Double impact;
        private String description;
        private Map<String, Object> factorData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestigationAction {
        private String actionId;
        private String actionType;
        private String description;
        private LocalDateTime performedAt;
        private String performedBy;
        private String result;
        private Map<String, Object> actionData;
    }
    
    /**
     * Check if investigation is overdue
     */
    public boolean isOverdue() {
        return dueDate != null && LocalDateTime.now().isAfter(dueDate) && 
               status != InvestigationStatus.CLOSED && status != InvestigationStatus.CANCELLED;
    }
    
    /**
     * Check if investigation is high priority
     */
    public boolean isHighPriority() {
        return priority == Priority.CRITICAL || priority == Priority.URGENT;
    }
    
    /**
     * Check if investigation is active
     */
    public boolean isActive() {
        return status == InvestigationStatus.OPEN || 
               status == InvestigationStatus.IN_PROGRESS ||
               status == InvestigationStatus.PENDING_REVIEW ||
               status == InvestigationStatus.ESCALATED;
    }
    
    /**
     * Get investigation duration in hours
     */
    public Long getInvestigationDurationHours() {
        if (createdAt == null) {
            return null;
        }
        
        LocalDateTime endTime = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(createdAt, endTime).toHours();
    }
    
    /**
     * Get number of findings
     */
    public int getFindingsCount() {
        return findings != null ? findings.size() : 0;
    }
    
    /**
     * Get number of evidence items
     */
    public int getEvidenceCount() {
        return evidence != null ? evidence.size() : 0;
    }
    
    /**
     * Check if fraud is confirmed
     */
    public boolean isFraudConfirmed() {
        return outcome == InvestigationOutcome.FRAUD_CONFIRMED;
    }
}