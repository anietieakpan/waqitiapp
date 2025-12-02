package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Privacy Audit Event - comprehensive audit trail for GDPR operations
 *
 * Maintains detailed logs of all privacy-related operations for
 * accountability and compliance demonstration under Article 5(2)
 * and Article 24 (accountability principle).
 */
@Entity
@Table(name = "privacy_audit_events",
       indexes = {
           @Index(name = "idx_audit_event_type", columnList = "event_type"),
           @Index(name = "idx_audit_entity_type", columnList = "entity_type"),
           @Index(name = "idx_audit_entity_id", columnList = "entity_id"),
           @Index(name = "idx_audit_user_id", columnList = "user_id"),
           @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
           @Index(name = "idx_audit_correlation_id", columnList = "correlation_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyAuditEvent {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column(name = "id", length = 36)
    private String id;

    @NotBlank
    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @NotBlank
    @Column(name = "entity_type", length = 100, nullable = false)
    private String entityType;

    @NotBlank
    @Column(name = "entity_id", length = 255, nullable = false)
    private String entityId;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "performed_by", length = 255)
    private String performedBy;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 50)
    private AuditAction action;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "privacy_audit_details",
                     joinColumns = @JoinColumn(name = "audit_event_id"))
    @MapKeyColumn(name = "detail_key", length = 100)
    @Column(name = "detail_value", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> details = new HashMap<>();

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20)
    private AuditResult result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    // Data subject rights tracking
    @Enumerated(EnumType.STRING)
    @Column(name = "privacy_right", length = 50)
    private PrivacyRight privacyRight;

    @Column(name = "legal_basis", length = 100)
    private String legalBasis;

    // Compliance tracking
    @Column(name = "gdpr_article", length = 50)
    private String gdprArticle;

    @Column(name = "retention_period_days")
    private Integer retentionPeriodDays;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Metadata
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        // Default retention: 7 years for GDPR compliance
        if (retentionPeriodDays == null) {
            retentionPeriodDays = 2555; // 7 years
        }
        if (expiresAt == null && retentionPeriodDays != null) {
            expiresAt = timestamp.plusDays(retentionPeriodDays);
        }
    }

    /**
     * Add detail to audit event
     */
    public void addDetail(String key, String value) {
        if (this.details == null) {
            this.details = new HashMap<>();
        }
        this.details.put(key, value);
    }

    /**
     * Add multiple details
     */
    public void addDetails(Map<String, String> additionalDetails) {
        if (this.details == null) {
            this.details = new HashMap<>();
        }
        this.details.putAll(additionalDetails);
    }

    /**
     * Mark audit as successful
     */
    public void markSuccess() {
        this.result = AuditResult.SUCCESS;
    }

    /**
     * Mark audit as failed with error
     */
    public void markFailure(String error) {
        this.result = AuditResult.FAILURE;
        this.errorMessage = error;
    }

    /**
     * Check if audit event has expired (for retention)
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
