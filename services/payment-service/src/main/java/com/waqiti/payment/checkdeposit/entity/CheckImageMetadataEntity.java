package com.waqiti.payment.checkdeposit.entity;

import com.waqiti.payment.checkdeposit.service.S3ImageStorageService;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PRODUCTION ENTITY: Check Image Metadata Storage
 *
 * Persists metadata for check images stored in S3/AWS for:
 * - Audit trail and compliance (Check 21 Act - 7 year retention)
 * - Encryption key tracking
 * - Virus scan results
 * - Image integrity verification (SHA-256 checksums)
 * - Financial regulatory compliance
 *
 * COMPLIANCE:
 * -----------
 * - Check 21 Act: Requires 7-year retention of check images
 * - SOX: Audit trail of all financial document access
 * - PCI-DSS: Encryption key management and versioning
 * - NACHA: Image quality and authenticity verification
 *
 * DATABASE INDEXES:
 * ----------------
 * - checkDepositId: Fast lookup by check deposit
 * - objectKey: Fast lookup by S3 object key (unique)
 * - uploadedByUserId: User audit queries
 * - expiresAt: Retention policy enforcement
 *
 * @author Waqiti Production Team
 * @version 2.0.0
 * @since November 18, 2025
 */
@Entity
@Table(name = "check_image_metadata", schema = "payment", indexes = {
    @Index(name = "idx_check_deposit_id", columnList = "check_deposit_id"),
    @Index(name = "idx_object_key", columnList = "object_key", unique = true),
    @Index(name = "idx_uploaded_by_user_id", columnList = "uploaded_by_user_id"),
    @Index(name = "idx_expires_at", columnList = "expires_at"),
    @Index(name = "idx_uploaded_at", columnList = "uploaded_at"),
    @Index(name = "idx_virus_scan_result", columnList = "virus_scan_result")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckImageMetadataEntity {

    /**
     * Primary key - auto-generated
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Unique identifier for the check deposit (business key)
     * Links to CheckDeposit entity
     */
    @Column(name = "check_deposit_id", nullable = false, length = 50)
    private String checkDepositId;

    /**
     * Type of check image (FRONT or BACK)
     * Stored as string enum for database portability
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 10)
    private S3ImageStorageService.ImageType imageType;

    /**
     * S3 object key (path) for the stored image
     * Must be unique across all stored images
     */
    @Column(name = "object_key", nullable = false, length = 500, unique = true)
    private String objectKey;

    /**
     * S3 bucket name where image is stored
     */
    @Column(name = "bucket_name", nullable = false, length = 100)
    private String bucketName;

    /**
     * Original size of image in bytes (before encryption)
     */
    @Column(name = "original_size_bytes", nullable = false)
    private Long originalSizeBytes;

    /**
     * Encrypted size of image in bytes (after encryption)
     */
    @Column(name = "encrypted_size_bytes")
    private Long encryptedSizeBytes;

    /**
     * Whether the image is encrypted
     */
    @Column(name = "encrypted", nullable = false)
    private Boolean encrypted;

    /**
     * ID of the encryption key used (AWS KMS Key ID or local key ID)
     * Critical for decryption operations
     */
    @Column(name = "encryption_key_id", length = 200)
    private String encryptionKeyId;

    /**
     * Timestamp when image was uploaded
     */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /**
     * Timestamp when image expires (7 years for Check 21 Act compliance)
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Content type of the image (image/jpeg, image/png, etc.)
     */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /**
     * SHA-256 checksum of the original image (for integrity verification)
     * Critical for detecting tampering or corruption
     */
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSHA256;

    /**
     * S3 version ID (for versioning support)
     * Enables recovery from accidental deletion
     */
    @Column(name = "version_id", length = 100)
    private String versionId;

    /**
     * AWS region where image is stored
     */
    @Column(name = "region", length = 50)
    private String region;

    /**
     * Whether virus scanning was performed
     */
    @Column(name = "virus_scanned", nullable = false)
    @Builder.Default
    private Boolean virusScanned = false;

    /**
     * Result of virus scan (CLEAN, INFECTED, PENDING, FAILED)
     */
    @Column(name = "virus_scan_result", length = 20)
    private String virusScanResult;

    /**
     * Timestamp of virus scan
     */
    @Column(name = "virus_scanned_at")
    private LocalDateTime virusScannedAt;

    /**
     * Image width in pixels
     */
    @Column(name = "image_width")
    private Integer imageWidth;

    /**
     * Image height in pixels
     */
    @Column(name = "image_height")
    private Integer imageHeight;

    /**
     * Image format (JPEG, PNG, TIFF, etc.)
     */
    @Column(name = "image_format", length = 20)
    private String imageFormat;

    /**
     * User ID who uploaded the image
     * Critical for audit trail and compliance
     */
    @Column(name = "uploaded_by_user_id", nullable = false, length = 50)
    private String uploadedByUserId;

    /**
     * Whether the image has been archived (moved to Glacier)
     */
    @Column(name = "archived", nullable = false)
    @Builder.Default
    private Boolean archived = false;

    /**
     * Timestamp when image was archived
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /**
     * Tags associated with the image (stored as JSONB)
     * Uses PostgreSQL JSONB for efficient querying
     */
    @Type(JsonBinaryType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private Map<String, String> tags;

    // ========================================================================
    // AUDIT FIELDS
    // ========================================================================

    /**
     * Timestamp when record was created (audit trail)
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when record was last updated (audit trail)
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * User ID who created the record (audit trail)
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    /**
     * User ID who last updated the record (audit trail)
     */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    // ========================================================================
    // SOFT DELETE SUPPORT
    // ========================================================================

    /**
     * Soft delete flag - allows recovery of accidentally deleted records
     */
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    /**
     * Timestamp when record was soft-deleted
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * User ID who deleted the record
     */
    @Column(name = "deleted_by", length = 50)
    private String deletedBy;

    // ========================================================================
    // JPA LIFECYCLE CALLBACKS
    // ========================================================================

    /**
     * Set creation timestamp before persisting
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        if (encrypted == null) {
            encrypted = false;
        }
        if (virusScanned == null) {
            virusScanned = false;
        }
        if (archived == null) {
            archived = false;
        }
        if (deleted == null) {
            deleted = false;
        }
    }

    /**
     * Update timestamp before updating
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ========================================================================
    // BUSINESS METHODS
    // ========================================================================

    /**
     * Check if image has expired per Check 21 Act retention policy
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if image is pending virus scan
     */
    public boolean isVirusScanPending() {
        return virusScanned && "PENDING".equals(virusScanResult);
    }

    /**
     * Check if image is infected
     */
    public boolean isInfected() {
        return virusScanned && "INFECTED".equals(virusScanResult);
    }

    /**
     * Check if image is clean and ready for use
     */
    public boolean isClean() {
        return virusScanned && "CLEAN".equals(virusScanResult);
    }

    /**
     * Soft delete the record
     */
    public void softDelete(String deletedByUserId) {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
    }

    /**
     * Mark as archived
     */
    public void markAsArchived() {
        this.archived = true;
        this.archivedAt = LocalDateTime.now();
    }
}
