package com.waqiti.card.entity;

import com.waqiti.card.enums.CardAuditEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardAuditLog - Immutable audit trail for all card operations
 *
 * PCI-DSS Requirement 10: Track and monitor all access to card data
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "card_audit_log", indexes = {
        @Index(name = "idx_audit_card_id", columnList = "card_id"),
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_card_timestamp", columnList = "card_id, timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardAuditLog {

    @Id
    @Column(name = "audit_id", updatable = false, nullable = false)
    private UUID auditId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "card_id")
    private UUID cardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private CardAuditEventType eventType;

    @Column(name = "event_description", length = 500)
    private String eventDescription;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // Immutable audit log - no setters allowed after creation
    @PrePersist
    protected void onCreate() {
        if (this.auditId == null) {
            this.auditId = UUID.randomUUID();
        }
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    // Prevent updates to audit logs
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Audit logs are immutable and cannot be updated");
    }
}
