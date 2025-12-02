package com.waqiti.billpayment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log for all bill payment operations
 * Provides complete audit trail for compliance and debugging
 */
@Entity
@Table(name = "bill_payment_audit_logs", indexes = {
        @Index(name = "idx_audit_entity_type", columnList = "entity_type"),
        @Index(name = "idx_audit_entity_id", columnList = "entity_id"),
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_entity_action", columnList = "entity_type, entity_id, action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillPaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "previous_state", columnDefinition = "JSONB")
    private String previousState;

    @Column(name = "new_state", columnDefinition = "JSONB")
    private String newState;

    @Column(name = "changes", columnDefinition = "JSONB")
    private String changes;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    // Business logic methods

    public static BillPaymentAuditLog createFor(String entityType, UUID entityId, String action, String userId) {
        return BillPaymentAuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .userId(userId)
                .build();
    }
}
