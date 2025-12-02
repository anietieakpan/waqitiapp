package com.waqiti.compliance.model.sanctions;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sanctions List Metadata Entity
 *
 * Tracks versions and metadata for all sanctions lists (OFAC, EU, UN).
 * Maintains version history for audit compliance and change detection.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sanctions_list_metadata", indexes = {
        @Index(name = "idx_sanctions_list_source", columnList = "list_source"),
        @Index(name = "idx_sanctions_list_active", columnList = "is_active,list_source"),
        @Index(name = "idx_sanctions_list_version_date", columnList = "version_date"),
        @Index(name = "idx_sanctions_list_download", columnList = "download_timestamp"),
        @Index(name = "idx_sanctions_list_status", columnList = "processing_status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_sanctions_list_version", columnNames = {"list_source", "version_id"})
})
public class SanctionsListMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    // List identification
    @Column(name = "list_source", nullable = false, length = 20)
    private String listSource; // OFAC, EU, UN

    @Column(name = "list_name", nullable = false, length = 100)
    private String listName;

    @Column(name = "list_type", nullable = false, length = 50)
    private String listType; // SDN, CONSOLIDATED, TARGETED, etc.

    // Version tracking
    @Column(name = "version_id", nullable = false, length = 50)
    private String versionId;

    @Column(name = "version_date", nullable = false)
    private LocalDateTime versionDate;

    @Column(name = "download_timestamp", nullable = false)
    private LocalDateTime downloadTimestamp;

    // List statistics
    @Column(name = "total_entries", nullable = false)
    private Integer totalEntries = 0;

    @Column(name = "new_entries")
    private Integer newEntries = 0;

    @Column(name = "modified_entries")
    private Integer modifiedEntries = 0;

    @Column(name = "removed_entries")
    private Integer removedEntries = 0;

    // Source information
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_file_size_bytes")
    private Long sourceFileSizeBytes;

    @Column(name = "source_file_hash", length = 64)
    private String sourceFileHash; // SHA-256 hash

    // Processing status
    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processing_completed_at")
    private LocalDateTime processingCompletedAt;

    @Column(name = "processing_duration_ms")
    private Integer processingDurationMs;

    @Column(name = "processing_error", columnDefinition = "TEXT")
    private String processingError;

    // Activation
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    // Audit fields
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy = "SYSTEM";

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (downloadTimestamp == null) {
            downloadTimestamp = LocalDateTime.now();
        }
    }
}
