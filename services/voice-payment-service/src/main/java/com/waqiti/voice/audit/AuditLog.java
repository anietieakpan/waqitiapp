package com.waqiti.voice.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Log Entity
 *
 * Immutable audit trail for compliance and forensics
 *
 * Retention:
 * - PCI-DSS: 1 year online, 3+ years archive
 * - GDPR: As long as necessary for purpose
 * - SOC 2: 1 year minimum
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_event_category", columnList = "event_category"),
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_severity", columnList = "severity"),
        @Index(name = "idx_audit_resource", columnList = "resource_type,resource_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_category", nullable = false, length = 50)
    private AuditLogService.EventCategory eventCategory;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    @Builder.Default
    private AuditLogService.Severity severity = AuditLogService.Severity.INFO;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "service_name", length = 100)
    @Builder.Default
    private String serviceName = "voice-payment-service";

    @Column(name = "service_version", length = 50)
    private String serviceVersion;
}
