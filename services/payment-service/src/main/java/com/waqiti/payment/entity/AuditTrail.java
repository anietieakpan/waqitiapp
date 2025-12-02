package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit Trail entity for payment service operations
 * Tracks all significant actions and changes for compliance
 */
@Entity
@Table(name = "payment_audit_trail", indexes = {
    @Index(name = "idx_audit_entity_id", columnList = "entity_id"),
    @Index(name = "idx_audit_performed_at", columnList = "performed_at"),
    @Index(name = "idx_audit_entity_type", columnList = "entity_type"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_performed_by", columnList = "performed_by")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrail {
    
    @Id
    @Column(name = "audit_id")
    private UUID auditId;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;
    
    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;
    
    @Column(name = "action", nullable = false, length = 50)
    private String action;
    
    @Column(name = "performed_by")
    private UUID performedBy;
    
    @CreationTimestamp
    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "old_values", columnDefinition = "TEXT")
    private String oldValues;
    
    @Column(name = "new_values", columnDefinition = "TEXT")
    private String newValues;
    
    @Column(name = "checksum", length = 64)
    private String checksum;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "severity", length = 20)
    @Builder.Default
    private String severity = "INFO";
    
    @Column(name = "compliant")
    @Builder.Default
    private Boolean compliant = true;
    
    @Column(name = "archived")
    @Builder.Default
    private Boolean archived = false;
    
    @Column(name = "retention_required")
    @Builder.Default
    private Boolean retentionRequired = true;
    
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
    
    /**
     * Audit severity levels
     */
    public enum Severity {
        LOW,
        INFO,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Common audit actions
     */
    public static class Actions {
        public static final String CREATE = "CREATE";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
        public static final String VIEW = "VIEW";
        public static final String APPROVE = "APPROVE";
        public static final String REJECT = "REJECT";
        public static final String PROCESS = "PROCESS";
        public static final String COMPLETE = "COMPLETE";
        public static final String FAIL = "FAIL";
        public static final String REVERSE = "REVERSE";
        public static final String LOCK = "LOCK";
        public static final String UNLOCK = "UNLOCK";
        public static final String VALIDATE = "VALIDATE";
        public static final String RECONCILE = "RECONCILE";
    }
    
    /**
     * Entity types
     */
    public static class EntityTypes {
        public static final String LEDGER_TRANSACTION = "LEDGER_TRANSACTION";
        public static final String JOURNAL_ENTRY = "JOURNAL_ENTRY";
        public static final String LEDGER_ACCOUNT = "LEDGER_ACCOUNT";
        public static final String PAYMENT = "PAYMENT";
        public static final String WALLET = "WALLET";
        public static final String FUND_RESERVATION = "FUND_RESERVATION";
    }
    
    /**
     * Mark as archived
     */
    public void markAsArchived() {
        this.archived = true;
        this.archivedAt = LocalDateTime.now();
    }
    
    /**
     * Check if entry is ready for archival
     */
    public boolean isReadyForArchival(int retentionDays) {
        return !archived && 
               !retentionRequired && 
               performedAt.isBefore(LocalDateTime.now().minusDays(retentionDays));
    }
    
    /**
     * Check if high severity
     */
    public boolean isHighSeverity() {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity);
    }
}