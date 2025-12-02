package com.waqiti.payment.checkdeposit.mapper;

import com.waqiti.payment.checkdeposit.dto.CheckImageMetadata;
import com.waqiti.payment.checkdeposit.entity.CheckImageMetadataEntity;
import org.springframework.stereotype.Component;

/**
 * PRODUCTION MAPPER: CheckImageMetadata DTO ↔ Entity Conversion
 *
 * Provides bidirectional mapping between:
 * - CheckImageMetadata (DTO for service layer)
 * - CheckImageMetadataEntity (JPA entity for persistence layer)
 *
 * DESIGN PATTERN:
 * --------------
 * Uses manual mapping instead of MapStruct for:
 * - Better control over null handling
 * - Explicit audit field management
 * - Custom business logic during conversion
 *
 * @author Waqiti Production Team
 * @version 2.0.0
 * @since November 18, 2025
 */
@Component
public class CheckImageMetadataMapper {

    /**
     * Convert DTO to Entity for database persistence
     *
     * @param dto CheckImageMetadata DTO
     * @return CheckImageMetadataEntity for JPA
     */
    public CheckImageMetadataEntity toEntity(CheckImageMetadata dto) {
        if (dto == null) {
            return null;
        }

        return CheckImageMetadataEntity.builder()
                .checkDepositId(dto.getCheckDepositId())
                .imageType(dto.getImageType())
                .objectKey(dto.getObjectKey())
                .bucketName(dto.getBucketName())
                .originalSizeBytes(dto.getOriginalSizeBytes())
                .encryptedSizeBytes(dto.getEncryptedSizeBytes())
                .encrypted(dto.isEncrypted())
                .encryptionKeyId(dto.getEncryptionKeyId())
                .uploadedAt(dto.getUploadedAt())
                .expiresAt(dto.getExpiresAt())
                .contentType(dto.getContentType())
                .checksumSHA256(dto.getChecksumSHA256())
                .versionId(dto.getVersionId())
                .region(dto.getRegion())
                .virusScanned(dto.isVirusScanned())
                .virusScanResult(dto.getVirusScanResult())
                .virusScannedAt(dto.getVirusScannedAt())
                .imageWidth(dto.getImageWidth())
                .imageHeight(dto.getImageHeight())
                .imageFormat(dto.getImageFormat())
                .uploadedByUserId(dto.getUploadedByUserId())
                .archived(dto.isArchived())
                .archivedAt(dto.getArchivedAt())
                .tags(dto.getTags())
                .build();
    }

    /**
     * Convert Entity to DTO for service layer
     *
     * @param entity CheckImageMetadataEntity from database
     * @return CheckImageMetadata DTO
     */
    public CheckImageMetadata toDto(CheckImageMetadataEntity entity) {
        if (entity == null) {
            return null;
        }

        return CheckImageMetadata.builder()
                .checkDepositId(entity.getCheckDepositId())
                .imageType(entity.getImageType())
                .objectKey(entity.getObjectKey())
                .bucketName(entity.getBucketName())
                .originalSizeBytes(entity.getOriginalSizeBytes())
                .encryptedSizeBytes(entity.getEncryptedSizeBytes())
                .encrypted(entity.getEncrypted())
                .encryptionKeyId(entity.getEncryptionKeyId())
                .uploadedAt(entity.getUploadedAt())
                .expiresAt(entity.getExpiresAt())
                .contentType(entity.getContentType())
                .checksumSHA256(entity.getChecksumSHA256())
                .versionId(entity.getVersionId())
                .region(entity.getRegion())
                .virusScanned(entity.getVirusScanned())
                .virusScanResult(entity.getVirusScanResult())
                .virusScannedAt(entity.getVirusScannedAt())
                .imageWidth(entity.getImageWidth())
                .imageHeight(entity.getImageHeight())
                .imageFormat(entity.getImageFormat())
                .uploadedByUserId(entity.getUploadedByUserId())
                .archived(entity.getArchived())
                .archivedAt(entity.getArchivedAt())
                .tags(entity.getTags())
                .build();
    }

    /**
     * Update existing entity with DTO values (for updates)
     * Preserves audit fields (createdAt, createdBy)
     *
     * @param entity Existing entity
     * @param dto Updated DTO values
     */
    public void updateEntity(CheckImageMetadataEntity entity, CheckImageMetadata dto) {
        if (entity == null || dto == null) {
            return;
        }

        // Update all fields except audit fields (createdAt, createdBy, id)
        entity.setCheckDepositId(dto.getCheckDepositId());
        entity.setImageType(dto.getImageType());
        entity.setObjectKey(dto.getObjectKey());
        entity.setBucketName(dto.getBucketName());
        entity.setOriginalSizeBytes(dto.getOriginalSizeBytes());
        entity.setEncryptedSizeBytes(dto.getEncryptedSizeBytes());
        entity.setEncrypted(dto.isEncrypted());
        entity.setEncryptionKeyId(dto.getEncryptionKeyId());
        entity.setUploadedAt(dto.getUploadedAt());
        entity.setExpiresAt(dto.getExpiresAt());
        entity.setContentType(dto.getContentType());
        entity.setChecksumSHA256(dto.getChecksumSHA256());
        entity.setVersionId(dto.getVersionId());
        entity.setRegion(dto.getRegion());
        entity.setVirusScanned(dto.isVirusScanned());
        entity.setVirusScanResult(dto.getVirusScanResult());
        entity.setVirusScannedAt(dto.getVirusScannedAt());
        entity.setImageWidth(dto.getImageWidth());
        entity.setImageHeight(dto.getImageHeight());
        entity.setImageFormat(dto.getImageFormat());
        entity.setUploadedByUserId(dto.getUploadedByUserId());
        entity.setArchived(dto.isArchived());
        entity.setArchivedAt(dto.getArchivedAt());
        entity.setTags(dto.getTags());

        // updatedAt and updatedBy will be set by @PreUpdate callback
    }
}
