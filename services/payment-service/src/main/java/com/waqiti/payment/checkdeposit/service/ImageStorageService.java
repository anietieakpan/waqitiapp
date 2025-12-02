package com.waqiti.payment.checkdeposit.service;

import com.waqiti.payment.checkdeposit.dto.CheckImageMetadata;

/**
 * Interface for Check Image Storage
 *
 * Implementations must provide:
 * - Secure storage of check images
 * - Encryption at rest
 * - 7-year retention (Check 21 Act)
 * - Immutable audit trail
 */
public interface ImageStorageService {

    /**
     * Store check image securely
     *
     * @param checkDepositId Unique check deposit identifier
     * @param imageType FRONT or BACK
     * @param imageData Raw image bytes (JPEG format)
     * @return Metadata about stored image
     */
    CheckImageMetadata storeImage(String checkDepositId,
                                  S3ImageStorageService.ImageType imageType,
                                  byte[] imageData);

    /**
     * Retrieve check image
     *
     * @param objectKey Storage key
     * @return Decrypted image bytes
     */
    byte[] retrieveImage(String objectKey);

    /**
     * Generate temporary presigned URL for image access
     *
     * @param objectKey Storage key
     * @return Presigned URL (expires in 15 minutes)
     */
    String generatePresignedUrl(String objectKey);

    /**
     * Delete check image (admin only)
     *
     * @param objectKey Storage key
     */
    void deleteImage(String objectKey);
}
