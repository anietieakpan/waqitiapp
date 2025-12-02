package com.waqiti.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Trail entity
 * Comprehensive audit logging for all accounting operations
 */
@Entity
@Table(name = "audit_trail", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @NotNull
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @NotNull
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "details", columnDefinition = "JSONB")
    private String details;

    @Column(name = "before_state", columnDefinition = "JSONB")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "JSONB")
    private String afterState;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
