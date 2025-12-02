package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Investigation Request
 * 
 * Request to create or update a fraud investigation.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationRequest {
    
    /**
     * Request type
     */
    private RequestType requestType;
    
    /**
     * Investigation ID (for updates)
     */
    private String investigationId;
    
    /**
     * Investigation type
     */
    private FraudInvestigation.InvestigationType investigationType;
    
    /**
     * Investigation priority
     */
    @Builder.Default
    private FraudInvestigation.Priority priority = FraudInvestigation.Priority.NORMAL;
    
    /**
     * Investigation title
     */
    private String title;
    
    /**
     * Investigation description
     */
    private String description;
    
    /**
     * Subject of investigation
     */
    private SubjectRequest subject;
    
    /**
     * Related transaction IDs
     */
    private List<String> transactionIds;
    
    /**
     * Related user IDs
     */
    private List<String> userIds;
    
    /**
     * Triggering alert ID
     */
    private String triggerAlertId;
    
    /**
     * Initial evidence to attach
     */
    private List<EvidenceRequest> initialEvidence;
    
    /**
     * Due date for investigation
     */
    private LocalDateTime dueDate;
    
    /**
     * Assigned investigator
     */
    private String assignedTo;
    
    /**
     * Investigation team members
     */
    private List<String> teamMembers;
    
    /**
     * Tags for categorization
     */
    private List<String> tags;
    
    /**
     * Additional notes
     */
    private String notes;
    
    /**
     * Request metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Auto-escalation rules
     */
    private EscalationRules escalationRules;
    
    /**
     * Notification settings
     */
    private NotificationSettings notificationSettings;
    
    /**
     * Whether to auto-assign based on workload
     */
    @Builder.Default
    private Boolean autoAssign = false;
    
    /**
     * Whether this is urgent
     */
    @Builder.Default
    private Boolean urgent = false;
    
    /**
     * External case reference
     */
    private String externalReference;
    
    /**
     * Customer impact level
     */
    private CustomerImpact customerImpact;
    
    /**
     * Business impact level
     */
    private BusinessImpact businessImpact;
    
    /**
     * Request timestamp
     */
    private LocalDateTime requestedAt;
    
    /**
     * User making the request
     */
    private String requestedBy;
    
    /**
     * Source system
     */
    private String requestSource;
    
    public enum RequestType {
        CREATE,
        UPDATE,
        ESCALATE,
        REASSIGN,
        CLOSE,
        REOPEN
    }
    
    public enum CustomerImpact {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum BusinessImpact {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectRequest {
        private String subjectType;
        private String subjectId;
        private String subjectName;
        private Map<String, Object> subjectDetails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceRequest {
        private String evidenceType;
        private String description;
        private String sourceSystem;
        private String dataLocation;
        private Map<String, Object> evidenceData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscalationRules {
        private Integer escalateAfterHours;
        private String escalateTo;
        private Boolean autoEscalate;
        private List<EscalationTrigger> triggers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscalationTrigger {
        private String triggerType;
        private String condition;
        private String escalationLevel;
        private String escalateTo;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {
        private Boolean notifyOnCreated;
        private Boolean notifyOnUpdated;
        private Boolean notifyOnEscalated;
        private Boolean notifyOnCompleted;
        private List<String> emailNotifications;
        private List<String> slackNotifications;
        private String webhookUrl;
    }
    
    /**
     * Check if this is a high priority request
     */
    public boolean isHighPriority() {
        return priority == FraudInvestigation.Priority.CRITICAL ||
               priority == FraudInvestigation.Priority.URGENT ||
               urgent ||
               customerImpact == CustomerImpact.CRITICAL ||
               businessImpact == BusinessImpact.CRITICAL;
    }
    
    /**
     * Check if immediate attention is needed
     */
    public boolean needsImmediateAttention() {
        return urgent ||
               priority == FraudInvestigation.Priority.URGENT ||
               customerImpact == CustomerImpact.CRITICAL;
    }
    
    /**
     * Validate required fields
     */
    public boolean isValid() {
        if (requestType == RequestType.CREATE) {
            return title != null && !title.trim().isEmpty() &&
                   investigationType != null &&
                   subject != null &&
                   subject.getSubjectType() != null &&
                   subject.getSubjectId() != null;
        } else if (requestType == RequestType.UPDATE) {
            return investigationId != null && !investigationId.trim().isEmpty();
        }
        return false;
    }
    
    /**
     * Get effective due date
     */
    public LocalDateTime getEffectiveDueDate() {
        if (dueDate != null) {
            return dueDate;
        }
        
        // Auto-calculate based on priority
        LocalDateTime now = requestedAt != null ? requestedAt : LocalDateTime.now();
        
        switch (priority) {
            case URGENT:
                return now.plusHours(4);
            case CRITICAL:
                return now.plusHours(8);
            case HIGH:
                return now.plusDays(1);
            case NORMAL:
                return now.plusDays(3);
            case LOW:
                return now.plusDays(7);
            default:
                return now.plusDays(3);
        }
    }
    
    /**
     * Check if auto-escalation is enabled
     */
    public boolean hasAutoEscalation() {
        return escalationRules != null && 
               escalationRules.getAutoEscalate() != null && 
               escalationRules.getAutoEscalate();
    }
}