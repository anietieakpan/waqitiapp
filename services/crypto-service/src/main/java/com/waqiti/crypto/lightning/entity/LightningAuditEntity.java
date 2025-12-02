package com.waqiti.crypto.lightning.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;

/**
 * Entity representing a Lightning Network audit log entry
 * Provides comprehensive audit trail for all Lightning operations
 */
@Entity
@Table(name = "lightning_audit_log", indexes = {
    @Index(name = "idx_audit_user", columnList = "userId"),
    @Index(name = "idx_audit_event_type", columnList = "eventType"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_severity", columnList = "severity"),
    @Index(name = "idx_audit_user_timestamp", columnList = "userId, timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"auditData", "clientInfo"})
public class LightningAuditEntity extends BaseEntity {

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LightningAuditEventType eventType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditSeverity severity;

    @Column(nullable = false)
    private Instant timestamp;

    @ElementCollection
    @CollectionTable(name = "lightning_audit_data", joinColumns = @JoinColumn(name = "audit_id"))
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value", columnDefinition = "TEXT")
    private Map<String, Object> auditData;

    @ElementCollection
    @CollectionTable(name = "lightning_audit_client_info", joinColumns = @JoinColumn(name = "audit_id"))
    @MapKeyColumn(name = "info_key")
    @Column(name = "info_value")
    private Map<String, Object> clientInfo;

    @Column
    private String correlationId;

    @Column
    private String sessionId;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @Column
    private String requestId;

    @Column
    private String sourceSystem;

    @Column
    private Boolean isCompliant;

    @Column
    private String complianceNotes;

    @Column
    private String reviewedBy;

    @Column
    private Instant reviewedAt;

    @Column
    private Boolean archived;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (severity == null) {
            severity = AuditSeverity.LOW;
        }
        if (sourceSystem == null) {
            sourceSystem = "waqiti-crypto-service";
        }
        if (isCompliant == null) {
            isCompliant = true;
        }
        if (archived == null) {
            archived = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    /**
     * Check if this is a critical security event
     */
    public boolean isCriticalSecurityEvent() {
        return eventType == LightningAuditEventType.SECURITY_EVENT && 
               severity == AuditSeverity.HIGH;
    }

    /**
     * Check if this event requires compliance review
     */
    public boolean requiresComplianceReview() {
        return (severity == AuditSeverity.HIGH || 
                eventType == LightningAuditEventType.PAYMENT_SENT ||
                eventType == LightningAuditEventType.INVOICE_SETTLED) &&
               reviewedBy == null;
    }

    /**
     * Check if audit event is within retention period
     */
    public boolean isWithinRetentionPeriod(int retentionDays) {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 24 * 60 * 60L);
        return timestamp.isAfter(cutoff);
    }

    /**
     * Get audit data value safely
     */
    public Object getAuditDataValue(String key) {
        return auditData != null ? auditData.get(key) : null;
    }

    /**
     * Get client info value safely
     */
    public Object getClientInfoValue(String key) {
        return clientInfo != null ? clientInfo.get(key) : null;
    }
}