package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud response decision model.
 * Encapsulates the complete decision tree output and action directives
 * for responding to detected fraud events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudResponseDecision {
    
    // Core decision
    private Decision decision;
    private String reason;
    private Double confidenceScore;
    private Integer priority;
    
    // Action directives
    private boolean shouldBlock;
    private boolean shouldReverse;
    private boolean shouldBlockUser;
    private boolean shouldBlacklistUser;
    private boolean shouldNotifyAuthorities;
    
    // Additional requirements
    private boolean requiresManualReview;
    private boolean requiresTwoFactorAuth;
    private boolean requiresCompliance;
    private boolean requiresEnhancedMonitoring;
    
    // Enhanced monitoring parameters
    private boolean enhancedMonitoring;
    private Integer monitoringDurationDays;
    private List<String> monitoringRules;
    
    // Compliance and regulatory
    private boolean sarRequired;
    private boolean amlReportRequired;
    private List<String> regulatoryActions;
    
    // Risk assessment
    private Map<String, Double> riskFactors;
    private Double aggregateRiskScore;
    private String riskCategory;
    
    // Decision metadata
    private LocalDateTime decisionTimestamp;
    private String decisionEngine;
    private String decisionVersion;
    private Map<String, Object> decisionContext;
    
    // Notification details
    private List<String> notificationChannels;
    private Map<String, String> notificationTemplates;
    private List<String> recipientGroups;
    
    // Evidence and justification
    private List<String> triggeredRules;
    private Map<String, Object> evidence;
    private String detailedJustification;
    
    // Response timeline
    private LocalDateTime responseDeadline;
    private Integer escalationTimeMinutes;
    private String escalationPath;
    
    // Audit trail
    private String auditId;
    private Map<String, Object> auditData;
    
    /**
     * Decision types enumeration
     */
    public enum Decision {
        ALLOW("Transaction allowed with logging"),
        MONITOR("Enhanced monitoring applied"),
        REVIEW("Manual review required"),
        CHALLENGE("Additional authentication required"),
        BLOCK("Transaction blocked"),
        BLOCK_AND_REVIEW("Blocked pending review"),
        BLOCK_AND_REVERSE("Blocked and reversed"),
        BLOCK_AND_REPORT("Blocked with authority notification"),
        ESCALATE("Escalated to security team"),
        QUARANTINE("Account quarantined");
        
        private final String description;
        
        Decision(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Convenience methods for decision evaluation
     */
    public boolean isBlockingDecision() {
        return decision == Decision.BLOCK || 
               decision == Decision.BLOCK_AND_REVIEW || 
               decision == Decision.BLOCK_AND_REVERSE ||
               decision == Decision.BLOCK_AND_REPORT;
    }
    
    public boolean requiresHumanIntervention() {
        return requiresManualReview || 
               decision == Decision.REVIEW || 
               decision == Decision.ESCALATE;
    }
    
    public boolean isHighPriority() {
        return priority != null && priority <= 2;
    }
    
    public boolean requiresImmediateAction() {
        return isBlockingDecision() || isHighPriority() || shouldNotifyAuthorities;
    }
    
    public boolean hasComplianceImplications() {
        return requiresCompliance || sarRequired || amlReportRequired || 
               (regulatoryActions != null && !regulatoryActions.isEmpty());
    }
}