package com.waqiti.payment.checkdeposit.dto;

import com.waqiti.payment.checkdeposit.service.S3ImageStorageService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metadata for stored check images
 * Contains information about image storage, encryption, and retention
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckImageMetadata {

    /**
     * Unique identifier for the check deposit
     */
    private String checkDepositId;

    /**
     * Type of check image (FRONT or BACK)
     */
    private S3ImageStorageService.ImageType imageType;

    /**
     * S3 object key (path) for the stored image
     */
    private String objectKey;

    /**
     * S3 bucket name where image is stored
     */
    private String bucketName;

    /**
     * Original size of image in bytes (before encryption)
     */
    private long originalSizeBytes;

    /**
     * Encrypted size of image in bytes (after encryption)
     */
    private long encryptedSizeBytes;

    /**
     * Whether the image is encrypted
     */
    private boolean encrypted;

    /**
     * ID of the encryption key used
     */
    private String encryptionKeyId;

    /**
     * Timestamp when image was uploaded
     */
    private LocalDateTime uploadedAt;

    /**
     * Timestamp when image expires (7 years for Check 21 Act compliance)
     */
    private LocalDateTime expiresAt;

    /**
     * Content type of the image
     */
    private String contentType;

    /**
     * SHA-256 checksum of the original image (for integrity verification)
     */
    private String checksumSHA256;

    /**
     * S3 version ID (for versioning support)
     */
    private String versionId;

    /**
     * AWS region where image is stored
     */
    private String region;

    /**
     * Whether virus scanning was performed
     */
    private boolean virusScanned;

    /**
     * Result of virus scan (CLEAN, INFECTED, PENDING)
     */
    private String virusScanResult;

    /**
     * Timestamp of virus scan
     */
    private LocalDateTime virusScannedAt;

    /**
     * Image width in pixels
     */
    private Integer imageWidth;

    /**
     * Image height in pixels
     */
    private Integer imageHeight;

    /**
     * Image format (JPEG, PNG, etc.)
     */
    private String imageFormat;

    /**
     * User ID who uploaded the image
     */
    private String uploadedByUserId;

    /**
     * Whether the image has been archived
     */
    private boolean archived;

    /**
     * Timestamp when image was archived
     */
    private LocalDateTime archivedAt;

    /**
     * Tags associated with the image
     */
    private java.util.Map<String, String> tags;
}
