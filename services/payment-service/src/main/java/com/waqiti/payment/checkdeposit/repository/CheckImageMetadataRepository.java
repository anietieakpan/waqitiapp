package com.waqiti.payment.checkdeposit.repository;

import com.waqiti.payment.checkdeposit.entity.CheckImageMetadataEntity;
import com.waqiti.payment.checkdeposit.service.S3ImageStorageService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PRODUCTION REPOSITORY: Check Image Metadata Storage
 *
 * Provides database access for check image metadata with:
 * - CRUD operations
 * - Custom queries for audit and compliance
 * - Soft delete support
 * - Retention policy enforcement
 *
 * COMPLIANCE QUERIES:
 * ------------------
 * - findExpiredImages(): Check 21 Act retention enforcement
 * - findByUploadedByUserId(): SOX audit trail
 * - findInfectedImages(): Security monitoring
 * - findPendingVirusScan(): Virus scanning workflow
 *
 * PERFORMANCE:
 * -----------
 * All queries use indexed columns for optimal performance
 * - checkDepositId: Fast lookup by check deposit
 * - objectKey: Unique constraint for data integrity
 * - uploadedByUserId: User audit queries
 * - expiresAt: Retention policy queries
 *
 * @author Waqiti Production Team
 * @version 2.0.0
 * @since November 18, 2025
 */
@Repository
public interface CheckImageMetadataRepository extends JpaRepository<CheckImageMetadataEntity, Long> {

    // ========================================================================
    // PRIMARY LOOKUP METHODS
    // ========================================================================

    /**
     * Find metadata by S3 object key (unique identifier)
     *
     * @param objectKey S3 object key
     * @return Optional containing metadata if found
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.objectKey = :objectKey AND m.deleted = false")
    Optional<CheckImageMetadataEntity> findByObjectKey(@Param("objectKey") String objectKey);

    /**
     * Find all metadata for a specific check deposit
     * Typically returns 2 records (front and back images)
     *
     * @param checkDepositId Check deposit ID
     * @return List of metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.checkDepositId = :checkDepositId AND m.deleted = false ORDER BY m.imageType")
    List<CheckImageMetadataEntity> findByCheckDepositId(@Param("checkDepositId") String checkDepositId);

    /**
     * Find metadata for specific check deposit and image type
     *
     * @param checkDepositId Check deposit ID
     * @param imageType Image type (FRONT or BACK)
     * @return Optional containing metadata if found
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.checkDepositId = :checkDepositId AND m.imageType = :imageType AND m.deleted = false")
    Optional<CheckImageMetadataEntity> findByCheckDepositIdAndImageType(
            @Param("checkDepositId") String checkDepositId,
            @Param("imageType") S3ImageStorageService.ImageType imageType);

    // ========================================================================
    // AUDIT AND COMPLIANCE QUERIES
    // ========================================================================

    /**
     * Find all images uploaded by a specific user
     * Used for SOX compliance and user activity audits
     *
     * @param userId User ID
     * @return List of metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.uploadedByUserId = :userId AND m.deleted = false ORDER BY m.uploadedAt DESC")
    List<CheckImageMetadataEntity> findByUploadedByUserId(@Param("userId") String userId);

    /**
     * Find images uploaded within a date range
     * Used for regulatory reporting and audits
     *
     * @param startDate Start date
     * @param endDate End date
     * @return List of metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.uploadedAt BETWEEN :startDate AND :endDate AND m.deleted = false ORDER BY m.uploadedAt")
    List<CheckImageMetadataEntity> findByUploadedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // ========================================================================
    // RETENTION POLICY QUERIES
    // ========================================================================

    /**
     * Find expired images that should be deleted per Check 21 Act
     * Check 21 Act requires 7-year retention, after which images can be purged
     *
     * @return List of expired metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.expiresAt < CURRENT_TIMESTAMP AND m.deleted = false AND m.archived = false")
    List<CheckImageMetadataEntity> findExpiredImages();

    /**
     * Find images expiring within specified days
     * Used for proactive archival before expiration
     *
     * @param daysUntilExpiry Number of days until expiry
     * @return List of metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.expiresAt BETWEEN CURRENT_TIMESTAMP AND :expiryDate AND m.deleted = false AND m.archived = false")
    List<CheckImageMetadataEntity> findImagesExpiringWithinDays(@Param("expiryDate") LocalDateTime expiryDate);

    /**
     * Find images older than specified days
     * Used for archival to cheaper storage (S3 Glacier)
     *
     * @param cutoffDate Cutoff date
     * @return List of metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.uploadedAt < :cutoffDate AND m.deleted = false AND m.archived = false")
    List<CheckImageMetadataEntity> findImagesOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========================================================================
    // VIRUS SCANNING QUERIES
    // ========================================================================

    /**
     * Find images pending virus scan
     * Used by virus scanning workflow to process images
     *
     * @return List of metadata records pending scan
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.virusScanned = false OR m.virusScanResult = 'PENDING' AND m.deleted = false")
    List<CheckImageMetadataEntity> findPendingVirusScan();

    /**
     * Find infected images that need quarantine
     * Critical security monitoring query
     *
     * @return List of infected metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.virusScanResult = 'INFECTED' AND m.deleted = false")
    List<CheckImageMetadataEntity> findInfectedImages();

    /**
     * Find clean images ready for processing
     *
     * @return List of clean metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.virusScanResult = 'CLEAN' AND m.deleted = false")
    List<CheckImageMetadataEntity> findCleanImages();

    // ========================================================================
    // ENCRYPTION QUERIES
    // ========================================================================

    /**
     * Find images encrypted with a specific key
     * Used for key rotation operations
     *
     * @param encryptionKeyId Encryption key ID
     * @return List of metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.encryptionKeyId = :encryptionKeyId AND m.encrypted = true AND m.deleted = false")
    List<CheckImageMetadataEntity> findByEncryptionKeyId(@Param("encryptionKeyId") String encryptionKeyId);

    /**
     * Find unencrypted images (security vulnerability)
     * Should return empty list in production
     *
     * @return List of unencrypted metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.encrypted = false AND m.deleted = false")
    List<CheckImageMetadataEntity> findUnencryptedImages();

    // ========================================================================
    // ARCHIVAL QUERIES
    // ========================================================================

    /**
     * Find archived images
     *
     * @return List of archived metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.archived = true AND m.deleted = false")
    List<CheckImageMetadataEntity> findArchivedImages();

    /**
     * Find non-archived images
     *
     * @return List of non-archived metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.archived = false AND m.deleted = false")
    List<CheckImageMetadataEntity> findNonArchivedImages();

    // ========================================================================
    // SOFT DELETE OPERATIONS
    // ========================================================================

    /**
     * Soft delete metadata by object key
     * Sets deleted = true instead of physical deletion
     *
     * @param objectKey S3 object key
     * @param deletedBy User ID performing deletion
     * @return Number of records updated
     */
    @Modifying
    @Query("UPDATE CheckImageMetadataEntity m SET m.deleted = true, m.deletedAt = CURRENT_TIMESTAMP, m.deletedBy = :deletedBy WHERE m.objectKey = :objectKey")
    int softDeleteByObjectKey(@Param("objectKey") String objectKey, @Param("deletedBy") String deletedBy);

    /**
     * Soft delete all metadata for a check deposit
     *
     * @param checkDepositId Check deposit ID
     * @param deletedBy User ID performing deletion
     * @return Number of records updated
     */
    @Modifying
    @Query("UPDATE CheckImageMetadataEntity m SET m.deleted = true, m.deletedAt = CURRENT_TIMESTAMP, m.deletedBy = :deletedBy WHERE m.checkDepositId = :checkDepositId")
    int softDeleteByCheckDepositId(@Param("checkDepositId") String checkDepositId, @Param("deletedBy") String deletedBy);

    /**
     * Find soft-deleted records (for recovery)
     *
     * @return List of soft-deleted metadata records
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.deleted = true")
    List<CheckImageMetadataEntity> findDeleted();

    // ========================================================================
    // STATISTICS QUERIES
    // ========================================================================

    /**
     * Count total images stored
     *
     * @return Total count
     */
    @Query("SELECT COUNT(m) FROM CheckImageMetadataEntity m WHERE m.deleted = false")
    long countTotalImages();

    /**
     * Count encrypted images
     *
     * @return Encrypted images count
     */
    @Query("SELECT COUNT(m) FROM CheckImageMetadataEntity m WHERE m.encrypted = true AND m.deleted = false")
    long countEncryptedImages();

    /**
     * Count archived images
     *
     * @return Archived images count
     */
    @Query("SELECT COUNT(m) FROM CheckImageMetadataEntity m WHERE m.archived = true AND m.deleted = false")
    long countArchivedImages();

    /**
     * Calculate total storage used (original bytes)
     *
     * @return Total bytes
     */
    @Query("SELECT COALESCE(SUM(m.originalSizeBytes), 0) FROM CheckImageMetadataEntity m WHERE m.deleted = false")
    long calculateTotalStorageBytes();

    /**
     * Calculate total encrypted storage used
     *
     * @return Total encrypted bytes
     */
    @Query("SELECT COALESCE(SUM(m.encryptedSizeBytes), 0) FROM CheckImageMetadataEntity m WHERE m.encrypted = true AND m.deleted = false")
    long calculateTotalEncryptedStorageBytes();

    // ========================================================================
    // INTEGRITY VERIFICATION
    // ========================================================================

    /**
     * Find images with matching SHA-256 checksum (deduplication check)
     *
     * @param checksumSHA256 SHA-256 checksum
     * @return List of metadata records with matching checksum
     */
    @Query("SELECT m FROM CheckImageMetadataEntity m WHERE m.checksumSHA256 = :checksumSHA256 AND m.deleted = false")
    List<CheckImageMetadataEntity> findByChecksumSHA256(@Param("checksumSHA256") String checksumSHA256);

    /**
     * Check if object key exists (for duplicate prevention)
     *
     * @param objectKey S3 object key
     * @return True if exists
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM CheckImageMetadataEntity m WHERE m.objectKey = :objectKey AND m.deleted = false")
    boolean existsByObjectKey(@Param("objectKey") String objectKey);

    /**
     * Check if check deposit has both front and back images
     *
     * @param checkDepositId Check deposit ID
     * @return True if both images exist
     */
    @Query("SELECT CASE WHEN COUNT(m) = 2 THEN true ELSE false END FROM CheckImageMetadataEntity m WHERE m.checkDepositId = :checkDepositId AND m.deleted = false")
    boolean hasCompleteImageSet(@Param("checkDepositId") String checkDepositId);
}
