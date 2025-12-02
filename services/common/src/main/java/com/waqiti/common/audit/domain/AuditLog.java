package com.waqiti.common.audit.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.waqiti.common.audit.AuditEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Comprehensive Audit Log Entity for SOX, PCI DSS, GDPR, and SOC 2 Compliance
 * 
 * This entity captures all audit events across the platform with compliance-specific
 * metadata and retention policies. Designed for immutability and tamper detection.
 * 
 * COMPLIANCE MAPPING:
 * - PCI DSS: Tracks cardholder data access, system events, user authentication
 * - SOX: Financial transaction audit trails, configuration changes, access controls
 * - GDPR: Personal data access, consent changes, data exports/deletions
 * - SOC 2: Security monitoring, system availability, operational controls
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_event", columnList = "event_type"),
    @Index(name = "idx_audit_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_audit_category", columnList = "event_category"),
    @Index(name = "idx_audit_severity", columnList = "severity"),
    @Index(name = "idx_audit_session", columnList = "session_id"),
    @Index(name = "idx_audit_correlation", columnList = "correlation_id"),
    @Index(name = "idx_audit_compliance", columnList = "pci_relevant, gdpr_relevant, sox_relevant"),
    @Index(name = "idx_audit_retention", columnList = "retention_until"),
    @Index(name = "idx_audit_result", columnList = "result"),
    @Index(name = "idx_audit_ip", columnList = "ip_address")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    // Immutable sequence number for tamper detection
    @Column(name = "sequence_number", nullable = false, unique = true)
    private Long sequenceNumber;
    
    // Timestamp fields
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    @Column(name = "timestamp_utc", nullable = false)
    private LocalDateTime timestampUtc;
    
    // Event identification
    @Column(name = "event_id", length = 100, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;

    @Column(name = "event_category", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventCategory eventCategory;
    
    @Column(name = "severity", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Severity severity;
    
    // User and session information
    @Column(name = "user_id", length = 100)
    private String userId;
    
    @Column(name = "username", length = 255)
    private String username;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "correlation_id", length = 100)
    private String correlationId;
    
    @Column(name = "service", length = 100)
    private String service;
    
    // Entity being acted upon
    @Column(name = "entity_type", length = 100)
    private String entityType;
    
    @Column(name = "entity_id", length = 100)
    private String entityId;
    
    // Action and description
    @Column(name = "action", nullable = false, length = 255)
    private String action;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    // Metadata as JSON
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    // Network and device information
    @Column(name = "ip_address", length = 45) // IPv6 compatible
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "device_id", length = 100)
    private String deviceId;
    
    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;
    
    // Geolocation
    @Column(name = "location_country", length = 3)
    private String locationCountry;
    
    @Column(name = "location_region", length = 100)
    private String locationRegion;
    
    @Column(name = "location_city", length = 100)
    private String locationCity;
    
    // Operation result
    @Column(name = "result", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OperationResult result;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "error_code", length = 50)
    private String errorCode;
    
    // Risk and compliance flags
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "fraud_indicators", columnDefinition = "TEXT")
    private String fraudIndicators;
    
    // Compliance tracking
    @Column(name = "pci_relevant", nullable = false)
    @Builder.Default
    private Boolean pciRelevant = false;
    
    @Column(name = "gdpr_relevant", nullable = false)
    @Builder.Default
    private Boolean gdprRelevant = false;
    
    @Column(name = "sox_relevant", nullable = false)
    @Builder.Default
    private Boolean soxRelevant = false;
    
    @Column(name = "soc2_relevant", nullable = false)
    @Builder.Default
    private Boolean soc2Relevant = false;
    
    // Data retention
    @Column(name = "retention_until", nullable = false)
    private LocalDateTime retentionUntil;
    
    @Column(name = "retention_policy", length = 50)
    private String retentionPolicy;
    
    // Integrity and tamper detection
    @Column(name = "hash", length = 255)
    private String hash;
    
    @Column(name = "previous_hash", length = 255)
    private String previousHash;
    
    @Column(name = "signature", length = 500)
    private String signature;
    
    // Archival status
    @Column(name = "archived", nullable = false)
    @Builder.Default
    private Boolean archived = false;
    
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
    
    @Column(name = "archive_location", length = 255)
    private String archiveLocation;
    
    // Additional compliance fields
    @Column(name = "requires_notification", nullable = false)
    @Builder.Default
    private Boolean requiresNotification = false;
    
    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;
    
    @Column(name = "investigation_required", nullable = false)
    @Builder.Default
    private Boolean investigationRequired = false;
    
    // Version for optimistic locking
    @Version
    @JsonIgnore
    private Long version;
    
    /**
     * Event categories for compliance classification
     */
    public enum EventCategory {
        FINANCIAL,          // Financial transactions, payments, transfers
        SECURITY,           // Authentication, authorization, security events
        DATA_ACCESS,        // PII access, data exports, queries
        CONFIGURATION,      // System configuration changes
        ADMIN,             // Administrative actions
        COMPLIANCE,        // AML, KYC, sanctions screening
        FRAUD,             // Fraud detection and prevention
        AUDIT,             // Audit-related events
        SYSTEM,            // System events, health checks
        API,               // API requests and responses
        NOTIFICATION,      // Alerts and notifications
        BATCH,             // Batch processing events
        INTEGRATION        // Third-party integrations
    }
    
    /**
     * Severity levels for audit events
     */
    public enum Severity {
        INFO,              // Informational events
        LOW,               // Low priority events
        WARNING,           // Warning events
        MEDIUM,            // Medium priority events
        HIGH,              // High priority events requiring attention
        CRITICAL,          // Critical events requiring immediate action
        EMERGENCY          // Emergency events requiring immediate response
    }
    
    /**
     * Operation result status
     */
    public enum OperationResult {
        SUCCESS,           // Operation completed successfully
        FAILURE,           // Operation failed
        PENDING,           // Operation is pending
        CANCELLED,         // Operation was cancelled
        TIMEOUT,           // Operation timed out
        BLOCKED,           // Operation was blocked
        RETRY,             // Operation will be retried
        PARTIAL           // Operation partially completed
    }
    
    /**
     * Pre-persist hook to set defaults
     */
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (timestampUtc == null) {
            timestampUtc = LocalDateTime.now();
        }
        if (retentionUntil == null) {
            // Default retention based on compliance requirements
            if (Boolean.TRUE.equals(soxRelevant)) {
                retentionUntil = timestampUtc.plusYears(7); // SOX requirement
            } else if (Boolean.TRUE.equals(pciRelevant)) {
                retentionUntil = timestampUtc.plusYears(1); // PCI DSS minimum
            } else if (Boolean.TRUE.equals(gdprRelevant)) {
                retentionUntil = timestampUtc.plusYears(3); // GDPR typical retention
            } else {
                retentionUntil = timestampUtc.plusYears(3); // Default retention
            }
        }
    }
    
    /**
     * Check if the audit log is eligible for archival
     */
    public boolean isEligibleForArchival() {
        return !archived && LocalDateTime.now().isAfter(retentionUntil.minusMonths(6));
    }
    
    /**
     * Check if the audit log should be purged
     */
    public boolean shouldBePurged() {
        return archived && LocalDateTime.now().isAfter(retentionUntil);
    }
    
    /**
     * Get compliance description
     */
    public String getComplianceDescription() {
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(pciRelevant)) sb.append("PCI DSS ");
        if (Boolean.TRUE.equals(gdprRelevant)) sb.append("GDPR ");
        if (Boolean.TRUE.equals(soxRelevant)) sb.append("SOX ");
        if (Boolean.TRUE.equals(soc2Relevant)) sb.append("SOC 2 ");
        return sb.toString().trim();
    }
}