package com.waqiti.common.gdpr;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Interface for GDPR-compliant data repositories
 *
 * All repositories storing personal data must implement this interface
 * to support GDPR rights (access, rectification, erasure, portability).
 *
 * Implementation example:
 * <pre>
 * {@literal @}Component
 * public class UserDataGDPRRepository implements GDPRDataRepository {
 *     {@literal @}Override
 *     public String getDataCategory() {
 *         return "user_profile";
 *     }
 *
 *     {@literal @}Override
 *     public Object getUserData(UUID userId) {
 *         return userRepository.findById(userId);
 *     }
 *
 *     // ... other methods
 * }
 * </pre>
 *
 * @author Waqiti Platform Team
 * @since 2025-10-19
 */
public interface GDPRDataRepository {

    /**
     * Get the category of data managed by this repository
     * Examples: "user_profile", "payment_history", "kyc_documents"
     */
    String getDataCategory();

    /**
     * Retrieve all personal data for a user (Article 15: Right to Access)
     *
     * @param userId The user ID
     * @return All personal data for this user in this category
     */
    Object getUserData(UUID userId);

    /**
     * Soft delete user data (mark as deleted, retain for legal period)
     * Used for "Right to be Forgotten" with legal retention requirements
     *
     * @param userId The user ID
     * @return true if data was deleted, false if no data found
     */
    boolean softDeleteUserData(UUID userId);

    /**
     * Hard delete user data (permanent removal)
     * Used when retention period expires or user explicitly requests
     *
     * @param userId The user ID
     * @return true if data was deleted, false if no data found
     */
    boolean hardDeleteUserData(UUID userId);

    /**
     * Rectify (correct) user data (Article 16: Right to Rectification)
     *
     * @param userId The user ID
     * @param corrections Map of field names to corrected values
     */
    void rectifyUserData(UUID userId, Map<String, Object> corrections);

    /**
     * Restrict processing of user data (Article 18)
     * Data can be stored but not processed
     *
     * @param userId The user ID
     */
    void restrictProcessing(UUID userId);

    /**
     * Resume processing of user data
     *
     * @param userId The user ID
     */
    void resumeProcessing(UUID userId);

    /**
     * Check if data must be retained for legal reasons
     * Examples: tax records (7 years), AML records (5 years)
     *
     * @param userId The user ID
     * @return true if data must be retained
     */
    boolean mustRetainForLegalReasons(UUID userId);

    /**
     * Get the retention period for this data category
     *
     * @return Retention duration (e.g., 7 years for financial records)
     */
    Duration getRetentionPeriod();

    /**
     * Get the legal basis for retention
     *
     * @return Legal basis (e.g., "Tax law Article 123", "AML regulations")
     */
    String getRetentionLegalBasis();
}
