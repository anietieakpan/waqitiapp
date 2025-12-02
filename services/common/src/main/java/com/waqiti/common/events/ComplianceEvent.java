package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compliance-related event for regulatory tracking
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceEvent extends FinancialEvent {

    @Getter(AccessLevel.NONE)
    private UUID eventId;
    private UUID entityId;

    @Getter(AccessLevel.NONE)
    private String eventType;
    private String complianceType;
    private String status;
    private String riskLevel;
    private String decision;
    private List<String> flags;
    private Map<String, Object> checkResults;
    private String regulatoryFramework;
    private String jurisdiction;
    private Instant timestamp;
    private String description;
    private Map<String, Object> metadata;

    @Getter(AccessLevel.NONE)
    private UUID correlationId;
    private String reviewer;
    private Instant reviewedAt;
    private List<String> requiredActions;

    // Override DomainEvent methods to convert UUID to String
    public String getEventId() {
        return eventId != null ? eventId.toString() : null;
    }

    public String getCorrelationId() {
        return correlationId != null ? correlationId.toString() : null;
    }

    /**
     * Compliance event types
     */
    public enum EventType {
        KYC_INITIATED,
        KYC_COMPLETED,
        KYC_FAILED,
        KYC_EXPIRED,
        AML_CHECK_INITIATED,
        AML_CHECK_COMPLETED,
        AML_CHECK_FAILED,
        SANCTIONS_CHECK_INITIATED,
        SANCTIONS_CHECK_COMPLETED,
        SANCTIONS_CHECK_FAILED,
        PEP_CHECK_INITIATED,
        PEP_CHECK_COMPLETED,
        PEP_CHECK_FAILED,
        RISK_ASSESSMENT_INITIATED,
        RISK_ASSESSMENT_COMPLETED,
        TRANSACTION_MONITORING_ALERT,
        SUSPICIOUS_ACTIVITY_DETECTED,
        REGULATORY_REPORT_FILED,
        COMPLIANCE_VIOLATION_DETECTED,
        COMPLIANCE_REVIEW_REQUIRED,
        COMPLIANCE_APPROVED,
        COMPLIANCE_REJECTED
    }
    
    /**
     * Create KYC initiated event
     */
    public static ComplianceEvent kycInitiated(UUID userId) {
        return ComplianceEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .entityId(userId)
            .eventType(EventType.KYC_INITIATED.name())
            .complianceType("KYC")
            .status("INITIATED")
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create KYC completed event
     */
    public static ComplianceEvent kycCompleted(UUID userId, String riskLevel) {
        return ComplianceEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .entityId(userId)
            .eventType(EventType.KYC_COMPLETED.name())
            .complianceType("KYC")
            .status("COMPLETED")
            .riskLevel(riskLevel)
            .decision("APPROVED")
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create AML check event
     */
    public static ComplianceEvent amlCheck(UUID entityId, UUID userId, String status, List<String> flags) {
        return ComplianceEvent.builder()
            .eventId(UUID.randomUUID())
            .entityId(entityId)
            .userId(userId)
            .eventType(status.equals("COMPLETED") ? 
                EventType.AML_CHECK_COMPLETED.name() : 
                EventType.AML_CHECK_FAILED.name())
            .complianceType("AML")
            .status(status)
            .flags(flags)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create sanctions check event
     */
    public static ComplianceEvent sanctionsCheck(UUID entityId, UUID userId, boolean matched, List<String> matches) {
        return ComplianceEvent.builder()
            .eventId(UUID.randomUUID())
            .entityId(entityId)
            .userId(userId)
            .eventType(EventType.SANCTIONS_CHECK_COMPLETED.name())
            .complianceType("SANCTIONS")
            .status(matched ? "MATCHED" : "CLEAR")
            .decision(matched ? "BLOCKED" : "APPROVED")
            .flags(matches)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create suspicious activity event
     */
    public static ComplianceEvent suspiciousActivity(UUID entityId, UUID userId, String activity, String riskLevel) {
        return ComplianceEvent.builder()
            .eventId(UUID.randomUUID())
            .entityId(entityId)
            .userId(userId)
            .eventType(EventType.SUSPICIOUS_ACTIVITY_DETECTED.name())
            .complianceType("MONITORING")
            .status("ALERT")
            .riskLevel(riskLevel)
            .description(activity)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Check if event requires review
     */
    public boolean requiresReview() {
        return "ALERT".equals(status) || 
               "MATCHED".equals(status) || 
               "HIGH".equals(riskLevel) ||
               (flags != null && !flags.isEmpty());
    }
    
    /**
     * Check if event is approved
     */
    public boolean isApproved() {
        return "APPROVED".equals(decision) || "CLEAR".equals(status);
    }
    
    /**
     * Check if event is blocked
     */
    public boolean isBlocked() {
        return "BLOCKED".equals(decision) || "REJECTED".equals(decision);
    }
    
    /**
     * Check if event is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
    
    /**
     * Get regulation that applies to this compliance event
     */
    public String getRegulation() {
        return regulatoryFramework != null ? regulatoryFramework : complianceType;
    }
}