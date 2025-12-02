package com.waqiti.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit log entry for ledger operations
 * 
 * This entity is write-once and cannot be updated or deleted
 * to ensure audit trail integrity and regulatory compliance.
 */
@Entity
@Table(name = "audit_log_entries",
    indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_performed_by", columnList = "performed_by"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_hash", columnList = "hash", unique = true),
        @Index(name = "idx_audit_correlation", columnList = "correlation_id")
    }
)
@Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {
    
    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;
    
    @CreationTimestamp
    @Column(name = "timestamp", updatable = false, nullable = false)
    private LocalDateTime timestamp;
    
    /**
     * Type of entity being audited (e.g., JOURNAL_ENTRY, ACCOUNT, TRANSACTION)
     */
    @Column(name = "entity_type", updatable = false, nullable = false, length = 50)
    private String entityType;
    
    /**
     * ID of the entity being audited
     */
    @Column(name = "entity_id", updatable = false, nullable = false, length = 100)
    private String entityId;
    
    /**
     * Action performed (e.g., CREATE, UPDATE, DELETE, APPROVE, REJECT)
     */
    @Column(name = "action", updatable = false, nullable = false, length = 50)
    private String action;
    
    /**
     * User who performed the action
     */
    @Column(name = "performed_by", updatable = false, nullable = false, length = 100)
    private String performedBy;
    
    /**
     * Role of the user who performed the action
     */
    @Column(name = "performed_by_role", updatable = false, length = 50)
    private String performedByRole;
    
    /**
     * IP address from which the action was performed
     */
    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;
    
    /**
     * User agent string
     */
    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;
    
    /**
     * Session ID for tracking user sessions
     */
    @Column(name = "session_id", updatable = false, length = 100)
    private String sessionId;
    
    /**
     * Correlation ID for distributed tracing
     */
    @Column(name = "correlation_id", updatable = false, length = 100)
    private String correlationId;
    
    /**
     * Serialized previous state of the entity
     */
    @Column(name = "previous_state", updatable = false, columnDefinition = "TEXT")
    private String previousState;
    
    /**
     * Serialized new state of the entity
     */
    @Column(name = "new_state", updatable = false, columnDefinition = "TEXT")
    private String newState;
    
    /**
     * Serialized changes between previous and new state
     */
    @Column(name = "changes", updatable = false, columnDefinition = "TEXT")
    private String changes;
    
    /**
     * Description of the action
     */
    @Column(name = "description", updatable = false, length = 1000)
    private String description;
    
    /**
     * Source system or service
     */
    @Column(name = "source", updatable = false, length = 100)
    private String source;
    
    /**
     * Whether the action was successful
     */
    @Column(name = "successful", updatable = false, nullable = false)
    private boolean successful;
    
    /**
     * Error message if the action failed
     */
    @Column(name = "error_message", updatable = false, columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * Hash of the previous entry for chain integrity
     */
    @Column(name = "previous_hash", updatable = false, nullable = false, length = 100)
    private String previousHash;
    
    /**
     * Cryptographic hash of this entry
     */
    @Column(name = "hash", updatable = false, nullable = false, unique = true, length = 100)
    private String hash;
    
    /**
     * HMAC for tamper detection
     */
    @Column(name = "hmac", updatable = false, nullable = false, length = 100)
    private String hmac;
    
    /**
     * Additional metadata as JSON
     */
    @ElementCollection
    @CollectionTable(name = "audit_log_metadata", 
        joinColumns = @JoinColumn(name = "audit_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, Object> metadata;
    
    /**
     * Archive status for compliance (logical deletion)
     */
    @Column(name = "archived", nullable = false)
    private boolean archived = false;
    
    /**
     * When this entry was archived
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
    
    /**
     * Reason for archiving
     */
    @Column(name = "archived_reason", length = 500)
    private String archivedReason;
    
    /**
     * Who archived this entry
     */
    @Column(name = "archived_by", length = 100)
    private String archivedBy;
    
    /**
     * Prevent updates to audit logs
     */
    @PreUpdate
    private void preventUpdate() {
        throw new UnsupportedOperationException("Audit log entries cannot be updated");
    }
    
    /**
     * Prevent deletion of audit logs
     */
    @PreRemove
    private void preventRemove() {
        throw new UnsupportedOperationException("Audit log entries cannot be deleted");
    }
}