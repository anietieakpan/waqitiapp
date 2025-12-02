package com.waqiti.audit.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "userId"),
    @Index(name = "idx_audit_event_type", columnList = "eventType"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_service", columnList = "serviceName"),
    @Index(name = "idx_audit_correlation", columnList = "correlationId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditResult result;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @ElementCollection
    @CollectionTable(name = "audit_event_metadata", 
                    joinColumns = @JoinColumn(name = "audit_event_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditSeverity severity;

    @Column(name = "compliance_tags")
    private String complianceTags;

    @Column(name = "retention_date")
    private Instant retentionDate;

    @Column(name = "archived", nullable = false)
    @Builder.Default
    private Boolean archived = false;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        // Set retention date based on compliance requirements (7 years for financial data)
        if (retentionDate == null) {
            retentionDate = timestamp.plusSeconds(7 * 365 * 24 * 60 * 60); // 7 years
        }
    }

    public enum AuditResult {
        SUCCESS,
        FAILURE,
        PARTIAL_SUCCESS,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        VALIDATION_ERROR,
        SYSTEM_ERROR
    }

    public enum AuditSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    // Helper methods for common audit scenarios
    public static AuditEvent createUserAction(String userId, String action, String description) {
        return AuditEvent.builder()
            .eventType("USER_ACTION")
            .userId(userId)
            .action(action)
            .description(description)
            .result(AuditResult.SUCCESS)
            .severity(AuditSeverity.LOW)
            .build();
    }

    public static AuditEvent createTransactionEvent(String transactionId, String userId, String action, AuditResult result) {
        return AuditEvent.builder()
            .eventType("TRANSACTION")
            .transactionId(transactionId)
            .userId(userId)
            .action(action)
            .result(result)
            .severity(result == AuditResult.SUCCESS ? AuditSeverity.MEDIUM : AuditSeverity.HIGH)
            .complianceTags("FINANCIAL,PCI_DSS")
            .build();
    }

    public static AuditEvent createSecurityEvent(String userId, String action, String description, AuditResult result) {
        return AuditEvent.builder()
            .eventType("SECURITY")
            .userId(userId)
            .action(action)
            .description(description)
            .result(result)
            .severity(result == AuditResult.SUCCESS ? AuditSeverity.MEDIUM : AuditSeverity.CRITICAL)
            .complianceTags("SECURITY,SOX")
            .build();
    }

    public static AuditEvent createDataAccessEvent(String userId, String resourceId, String resourceType, String action) {
        return AuditEvent.builder()
            .eventType("DATA_ACCESS")
            .userId(userId)
            .resourceId(resourceId)
            .resourceType(resourceType)
            .action(action)
            .result(AuditResult.SUCCESS)
            .severity(AuditSeverity.LOW)
            .complianceTags("DATA_ACCESS,GDPR")
            .build();
    }

    public static AuditEvent createSystemEvent(String serviceName, String action, String description, AuditResult result) {
        return AuditEvent.builder()
            .eventType("SYSTEM")
            .serviceName(serviceName)
            .action(action)
            .description(description)
            .result(result)
            .severity(result == AuditResult.SUCCESS ? AuditSeverity.LOW : AuditSeverity.HIGH)
            .build();
    }
}