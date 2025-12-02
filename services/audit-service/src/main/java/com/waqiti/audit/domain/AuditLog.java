package com.waqiti.audit.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core audit log entity for comprehensive audit trail
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
    @Index(name = "idx_audit_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_correlation", columnList = "correlation_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID auditId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "entity_type")
    private String entityType;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "action", nullable = false)
    private String action;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "source_ip_address")
    private String sourceIpAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;
    
    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;
    
    @Column(name = "change_details", columnDefinition = "TEXT")
    private String changeDetails;
    
    @Column(name = "business_context", columnDefinition = "TEXT")
    private String businessContext;
    
    @Column(name = "risk_level")
    private String riskLevel;
    
    @ElementCollection
    @CollectionTable(name = "audit_compliance_flags", 
                      joinColumns = @JoinColumn(name = "audit_id"))
    @Column(name = "flag")
    private List<String> complianceFlags;
    
    @ElementCollection
    @CollectionTable(name = "audit_metadata", 
                      joinColumns = @JoinColumn(name = "audit_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Column(name = "service_origin")
    private String serviceOrigin;
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @Column(name = "integrity_hash", unique = true)
    private String integrityHash;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "is_sensitive")
    private Boolean isSensitive;
    
    @Column(name = "retention_period_days")
    private Integer retentionPeriodDays;
    
    @Column(name = "is_archived")
    private Boolean isArchived;
    
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
    
    // Enhanced Compliance and Regulatory Fields
    @Column(name = "event_category")
    private String eventCategory;
    
    @Column(name = "severity")
    private String severity;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;
    
    @Column(name = "geo_location")
    private String geoLocation;
    
    @Column(name = "session_token")
    private String sessionToken;
    
    @ElementCollection
    @CollectionTable(name = "audit_regulatory_flags", 
                      joinColumns = @JoinColumn(name = "audit_id"))
    @Column(name = "regulatory_flag")
    private List<String> regulatoryFlags; // PCI_DSS, GDPR, SOX, AML, etc.
    
    @Column(name = "data_classification")
    private String dataClassification; // PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED
    
    @Column(name = "involves_pii")
    private Boolean involvesPII;
    
    @Column(name = "involves_pci_data")
    private Boolean involvesPCIData;
    
    @Column(name = "involves_financial_data")
    private Boolean involvesFinancialData;
    
    @Column(name = "previous_event_hash")
    private String previousEventHash;
    
    @Column(name = "tamper_detection_hash")
    private String tamperDetectionHash;
    
    @Column(name = "legal_hold")
    private Boolean legalHold;
    
    @Column(name = "compliance_validation_required")
    private Boolean complianceValidationRequired;
    
    @Column(name = "audit_trail_id")
    private String auditTrailId;

    @Column(name = "access_level")
    private String accessLevel;

    /**
     * Helper method for getId() - returns auditId as String
     */
    public String getId() {
        return auditId != null ? auditId.toString() : null;
    }

    /**
     * Helper method for getEventType() as enum
     */
    public com.waqiti.common.audit.AuditEventType getEventType() {
        if (eventType == null) return null;
        try {
            return com.waqiti.common.audit.AuditEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Helper method for setEventType() from enum
     */
    public void setEventType(com.waqiti.common.audit.AuditEventType type) {
        this.eventType = type != null ? type.name() : null;
    }

    /**
     * Helper method for getRiskLevel() as enum
     */
    public AuditRiskLevel getRiskLevel() {
        if (riskLevel == null) return null;
        try {
            return AuditRiskLevel.valueOf(riskLevel);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Helper method for setRiskLevel() from enum
     */
    public void setRiskLevel(AuditRiskLevel level) {
        this.riskLevel = level != null ? level.name() : null;
    }

    /**
     * Helper method to convert userId string to UUID
     */
    public UUID getUserId() {
        if (userId == null) return null;
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Helper method to set userId from UUID
     */
    public void setUserId(UUID userIdUuid) {
        this.userId = userIdUuid != null ? userIdUuid.toString() : null;
    }
}