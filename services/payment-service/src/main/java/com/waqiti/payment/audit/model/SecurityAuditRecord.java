package com.waqiti.payment.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Security Audit Record
 * 
 * Specialized audit record for security events including:
 * - Security violations and threat detection
 * - Authentication and authorization failures
 * - Suspicious behavior patterns
 * - Compliance violations
 * - Incident response tracking
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditRecord {
    
    // Record identification
    private String securityAuditId;
    private LocalDateTime timestamp;
    private ThreatLevel threatLevel;
    private SecurityEventType eventType;
    
    // Actor identification
    private UUID userId;
    private String userName;
    private String userEmail;
    private String ipAddress;
    private String geoLocation;
    private String deviceFingerprint;
    private boolean isKnownDevice;
    
    // Security event details
    private String violationType;
    private String violationDescription;
    private ViolationSeverity violationSeverity;
    private Map<String, Object> violationContext;
    private List<String> violationRules;
    
    // Threat intelligence
    private Integer riskScore;
    private List<RiskIndicator> riskIndicators;
    private String threatCategory;
    private boolean isKnownThreat;
    private String threatIntelSource;
    
    // Response actions
    private ResponseAction actionTaken;
    private boolean accountLocked;
    private boolean ipBlocked;
    private boolean requiresMfa;
    private boolean notificationSent;
    private List<String> notifiedParties;
    
    // Investigation details
    private InvestigationStatus investigationStatus;
    private String investigatorId;
    private LocalDateTime investigationStartTime;
    private LocalDateTime investigationEndTime;
    private String investigationNotes;
    private String resolution;
    
    // Compliance and reporting
    private boolean regulatoryReportRequired;
    private String regulatoryReportId;
    private LocalDateTime reportedAt;
    private String reportedTo;
    private ComplianceStandard complianceStandard;
    
    // Related events
    private List<String> relatedEventIds;
    private String parentEventId;
    private boolean isPartOfPattern;
    private String patternId;
    private Integer patternOccurrences;
    
    // System context
    private String sourceSystem;
    private String detectionMethod;
    private String preventionMechanism;
    private boolean automatedResponse;
    private String correlationId;
    
    // Evidence and artifacts
    private Map<String, String> evidenceLinks;
    private List<String> screenshotIds;
    private String logSnapshotId;
    private Map<String, Object> forensicData;
    
    // Enums
    public enum ThreatLevel {
        LOW(1),
        MEDIUM(2),
        HIGH(3),
        CRITICAL(4),
        EMERGENCY(5);
        
        private final int level;
        
        ThreatLevel(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    public enum SecurityEventType {
        AUTHENTICATION_FAILURE,
        AUTHORIZATION_VIOLATION,
        SUSPICIOUS_ACTIVITY,
        FRAUD_ATTEMPT,
        DATA_BREACH,
        POLICY_VIOLATION,
        COMPLIANCE_VIOLATION,
        MALICIOUS_ACTIVITY,
        SYSTEM_INTRUSION,
        INSIDER_THREAT
    }
    
    public enum ViolationSeverity {
        INFORMATIONAL,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum ResponseAction {
        NONE,
        LOGGED_ONLY,
        WARNING_ISSUED,
        ACCESS_DENIED,
        ACCOUNT_LOCKED,
        IP_BLOCKED,
        SESSION_TERMINATED,
        MFA_REQUIRED,
        MANUAL_REVIEW_REQUIRED,
        ESCALATED_TO_SECURITY
    }
    
    public enum InvestigationStatus {
        NOT_REQUIRED,
        PENDING,
        IN_PROGRESS,
        ON_HOLD,
        COMPLETED,
        CLOSED_FALSE_POSITIVE,
        CLOSED_CONFIRMED_THREAT,
        ESCALATED
    }
    
    public enum ComplianceStandard {
        PCI_DSS,
        SOC2,
        GDPR,
        CCPA,
        PSD2,
        AML,
        KYC,
        INTERNAL_POLICY
    }
    
    // Helper methods
    public boolean isHighRisk() {
        return threatLevel == ThreatLevel.HIGH || 
               threatLevel == ThreatLevel.CRITICAL || 
               threatLevel == ThreatLevel.EMERGENCY;
    }
    
    public boolean requiresImmediateAction() {
        return threatLevel == ThreatLevel.EMERGENCY ||
               (threatLevel == ThreatLevel.CRITICAL && eventType == SecurityEventType.SYSTEM_INTRUSION);
    }
    
    public boolean requiresInvestigation() {
        return investigationStatus != InvestigationStatus.NOT_REQUIRED &&
               investigationStatus != InvestigationStatus.COMPLETED &&
               investigationStatus != InvestigationStatus.CLOSED_FALSE_POSITIVE;
    }
    
    public boolean isRecurringThreat() {
        return isPartOfPattern && patternOccurrences != null && patternOccurrences > 3;
    }
    
    // Static factory methods
    public static SecurityAuditRecord authenticationFailure(UUID userId, String ipAddress, String reason) {
        return SecurityAuditRecord.builder()
            .securityAuditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .threatLevel(ThreatLevel.LOW)
            .eventType(SecurityEventType.AUTHENTICATION_FAILURE)
            .userId(userId)
            .ipAddress(ipAddress)
            .violationType("AUTHENTICATION_FAILURE")
            .violationDescription(reason)
            .violationSeverity(ViolationSeverity.LOW)
            .actionTaken(ResponseAction.LOGGED_ONLY)
            .investigationStatus(InvestigationStatus.NOT_REQUIRED)
            .build();
    }
    
    public static SecurityAuditRecord suspiciousActivity(UUID userId, String pattern, Map<String, Object> details) {
        return SecurityAuditRecord.builder()
            .securityAuditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .threatLevel(ThreatLevel.MEDIUM)
            .eventType(SecurityEventType.SUSPICIOUS_ACTIVITY)
            .userId(userId)
            .violationType("SUSPICIOUS_PATTERN")
            .violationDescription(pattern)
            .violationSeverity(ViolationSeverity.MEDIUM)
            .violationContext(details)
            .actionTaken(ResponseAction.MANUAL_REVIEW_REQUIRED)
            .investigationStatus(InvestigationStatus.PENDING)
            .build();
    }
    
    public static SecurityAuditRecord fraudAttempt(UUID userId, String ipAddress, 
                                                   String fraudType, Map<String, Object> evidence) {
        return SecurityAuditRecord.builder()
            .securityAuditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .threatLevel(ThreatLevel.CRITICAL)
            .eventType(SecurityEventType.FRAUD_ATTEMPT)
            .userId(userId)
            .ipAddress(ipAddress)
            .violationType(fraudType)
            .violationDescription("Fraud attempt detected: " + fraudType)
            .violationSeverity(ViolationSeverity.CRITICAL)
            .violationContext(evidence)
            .actionTaken(ResponseAction.ACCOUNT_LOCKED)
            .accountLocked(true)
            .investigationStatus(InvestigationStatus.IN_PROGRESS)
            .regulatoryReportRequired(true)
            .build();
    }
    
    // Supporting classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskIndicator {
        private String indicatorType;
        private String indicatorValue;
        private Integer weight;
        private String source;
        private LocalDateTime detectedAt;
    }
}