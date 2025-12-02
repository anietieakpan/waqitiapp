package com.waqiti.audit.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Log Entry Domain Entity
 */
@Entity
@Table(name = "audit_log_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String entityType;

    private String entityId;

    private String userId;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String encryptedData;

    @Column(columnDefinition = "TEXT")
    private String previousHash;

    @Column(columnDefinition = "TEXT")
    private String currentHash;

    private String encryptionKeyId;

    @Column(columnDefinition = "TEXT")
    private String iv;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    private Boolean verified;

    private Boolean archived;

    private LocalDateTime archivedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (verified == null) {
            verified = false;
        }
        if (archived == null) {
            archived = false;
        }
    }
}
