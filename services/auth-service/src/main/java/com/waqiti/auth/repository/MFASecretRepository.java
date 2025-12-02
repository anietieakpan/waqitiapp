package com.waqiti.auth.repository;

import com.waqiti.auth.domain.MFASecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for MFA Secret management
 *
 * SECURITY NOTES:
 * - All queries use parameterized statements (SQL injection protected)
 * - Sensitive data (secrets) should only be fetched when needed
 * - Failed attempts are tracked at database level for security
 */
@Repository
public interface MFASecretRepository extends JpaRepository<MFASecret, UUID> {

    /**
     * Find MFA secret by user ID
     */
    Optional<MFASecret> findByUserId(UUID userId);

    /**
     * Find enabled MFA secrets by user ID
     */
    Optional<MFASecret> findByUserIdAndEnabledTrue(UUID userId);

    /**
     * Check if user has MFA enabled
     */
    boolean existsByUserIdAndEnabledTrue(UUID userId);

    /**
     * Find all MFA secrets expiring within the given timeframe
     * Used for proactive rotation notifications
     */
    @Query("SELECT m FROM MFASecret m WHERE m.expiresAt BETWEEN :now AND :expiryThreshold AND m.enabled = true")
    List<MFASecret> findExpiringSecrets(
        @Param("now") LocalDateTime now,
        @Param("expiryThreshold") LocalDateTime expiryThreshold
    );

    /**
     * Find all expired MFA secrets that are still enabled
     * Used for automatic cleanup
     */
    @Query("SELECT m FROM MFASecret m WHERE m.expiresAt < :now AND m.enabled = true")
    List<MFASecret> findExpiredSecrets(@Param("now") LocalDateTime now);

    /**
     * Find MFA secrets with high failed attempts (potential brute force)
     */
    @Query("SELECT m FROM MFASecret m WHERE m.failedAttempts >= :threshold")
    List<MFASecret> findWithHighFailedAttempts(@Param("threshold") Integer threshold);

    /**
     * Increment failed attempt counter
     * ATOMIC operation to prevent race conditions
     */
    @Modifying
    @Query("UPDATE MFASecret m SET m.failedAttempts = m.failedAttempts + 1, " +
           "m.enabled = CASE WHEN m.failedAttempts + 1 >= 5 THEN false ELSE m.enabled END, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.userId = :userId")
    int incrementFailedAttempts(@Param("userId") UUID userId);

    /**
     * Reset failed attempts after successful verification
     */
    @Modifying
    @Query("UPDATE MFASecret m SET m.failedAttempts = 0, " +
           "m.lastVerifiedAt = CURRENT_TIMESTAMP, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.userId = :userId")
    int resetFailedAttempts(@Param("userId") UUID userId);

    /**
     * Use a backup code (decrement count)
     */
    @Modifying
    @Query("UPDATE MFASecret m SET m.backupCodesRemaining = m.backupCodesRemaining - 1, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.userId = :userId AND m.backupCodesRemaining > 0")
    int useBackupCode(@Param("userId") UUID userId);

    /**
     * Set device as trusted
     */
    @Modifying
    @Query("UPDATE MFASecret m SET m.trustedDevice = true, " +
           "m.trustedUntil = :trustedUntil, " +
           "m.deviceId = :deviceId, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.userId = :userId")
    int setTrustedDevice(
        @Param("userId") UUID userId,
        @Param("deviceId") String deviceId,
        @Param("trustedUntil") LocalDateTime trustedUntil
    );

    /**
     * Revoke device trust
     */
    @Modifying
    @Query("UPDATE MFASecret m SET m.trustedDevice = false, " +
           "m.trustedUntil = null, " +
           "m.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.userId = :userId")
    int revokeTrustedDevice(@Param("userId") UUID userId);

    /**
     * Find users with backup codes running low
     */
    @Query("SELECT m FROM MFASecret m WHERE m.backupCodesRemaining <= 2 AND m.enabled = true")
    List<MFASecret> findWithLowBackupCodes();

    /**
     * Count enabled MFA users
     */
    @Query("SELECT COUNT(m) FROM MFASecret m WHERE m.enabled = true")
    long countEnabledMFAUsers();

    /**
     * Find MFA secrets by method
     */
    List<MFASecret> findByMfaMethodAndEnabledTrue(MFASecret.MFAMethod method);

    /**
     * Delete MFA secret by user ID (for account deletion)
     */
    @Modifying
    @Query("DELETE FROM MFASecret m WHERE m.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
