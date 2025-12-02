package com.waqiti.common.gdpr.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Data Export Request Entity
 *
 * Implements GDPR Article 15 (Right to Access) and Article 20 (Right to Data Portability).
 * Tracks data export requests and their processing status.
 *
 * Security: Export files are encrypted and accessible only to the data subject.
 * Retention: Export files are automatically deleted after 30 days.
 */
@Entity
@Table(name = "gdpr_data_exports", indexes = {
    @Index(name = "idx_export_user_id", columnList = "user_id"),
    @Index(name = "idx_export_status", columnList = "status"),
    @Index(name = "idx_export_created", columnList = "created_at"),
    @Index(name = "idx_export_expires", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GDPRDataExport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "export_id", unique = true, nullable = false, length = 100)
    private String exportId;

    @NotNull
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ExportStatus status;

    @NotNull
    @Column(name = "format", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ExportFormat format;

    @Column(name = "include_metadata")
    private Boolean includeMetadata = true;

    @Column(name = "include_history")
    private Boolean includeHistory = true;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // Auto-delete after 30 days

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "encryption_key_id", length = 100)
    private String encryptionKeyId;

    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(name = "download_count")
    private Integer downloadCount = 0;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "data_categories", columnDefinition = "JSONB")
    private String dataCategories; // JSON array of included data types

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    public enum ExportStatus {
        PENDING,        // Request created, not yet started
        PROCESSING,     // Export generation in progress
        COMPLETED,      // Export ready for download
        DOWNLOADED,     // User has downloaded the export
        FAILED,         // Export generation failed
        EXPIRED,        // Export file expired and deleted
        CANCELLED       // Request cancelled by user
    }

    public enum ExportFormat {
        JSON,           // JSON format (machine-readable)
        XML,            // XML format
        CSV,            // CSV format (for tabular data)
        PDF,            // PDF format (human-readable)
        ZIP             // ZIP archive containing multiple formats
    }

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ExportStatus.PENDING;
        }
        if (exportId == null) {
            exportId = "EXPORT-" + UUID.randomUUID().toString();
        }
        // Set expiration to 30 days from creation
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusDays(30);
        }
    }

    /**
     * Check if export has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if export is ready for download
     */
    public boolean isReadyForDownload() {
        return status == ExportStatus.COMPLETED && !isExpired();
    }

    /**
     * Record a download
     */
    public void recordDownload() {
        this.downloadCount++;
        this.lastDownloadedAt = LocalDateTime.now();
        if (this.status == ExportStatus.COMPLETED) {
            this.status = ExportStatus.DOWNLOADED;
        }
    }
}
