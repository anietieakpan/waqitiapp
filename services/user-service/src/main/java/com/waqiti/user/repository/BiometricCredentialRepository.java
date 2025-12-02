package com.waqiti.user.repository;

import com.waqiti.user.domain.BiometricCredential;
import com.waqiti.user.dto.security.BiometricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BiometricCredential entity
 */
@Repository
public interface BiometricCredentialRepository extends JpaRepository<BiometricCredential, UUID> {

    /**
     * Find active biometric credential by user ID and type
     */
    Optional<BiometricCredential> findByUserIdAndBiometricTypeAndStatus(
            UUID userId, BiometricType biometricType, BiometricCredential.Status status);

    /**
     * Find all biometric credentials for a user
     */
    List<BiometricCredential> findByUserIdAndStatus(UUID userId, BiometricCredential.Status status);

    /**
     * Find biometric credential by credential ID
     */
    Optional<BiometricCredential> findByCredentialId(String credentialId);

    /**
     * Find biometric credential by credential ID and type
     */
    Optional<BiometricCredential> findByCredentialIdAndBiometricType(
            String credentialId, BiometricType biometricType);

    /**
     * Find biometric credential by credential ID and user ID
     */
    Optional<BiometricCredential> findByCredentialIdAndUserId(String credentialId, UUID userId);

    /**
     * Find all credentials for a device fingerprint
     */
    List<BiometricCredential> findByDeviceFingerprintAndStatus(
            String deviceFingerprint, BiometricCredential.Status status);

    /**
     * Count active biometric credentials for a user
     */
    @Query("SELECT COUNT(bc) FROM BiometricCredential bc WHERE bc.userId = :userId AND bc.status = :status")
    long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") BiometricCredential.Status status);

    /**
     * Count active biometric credentials by type for a user
     */
    @Query("SELECT COUNT(bc) FROM BiometricCredential bc WHERE bc.userId = :userId AND bc.biometricType = :type AND bc.status = :status")
    long countByUserIdAndBiometricTypeAndStatus(
            @Param("userId") UUID userId, 
            @Param("type") BiometricType biometricType, 
            @Param("status") BiometricCredential.Status status);

    /**
     * Find credentials that haven't been used for a specific period
     */
    @Query("SELECT bc FROM BiometricCredential bc WHERE bc.lastUsedAt < :cutoffDate AND bc.status = :status")
    List<BiometricCredential> findUnusedCredentials(
            @Param("cutoffDate") LocalDateTime cutoffDate, 
            @Param("status") BiometricCredential.Status status);

    /**
     * Find expired credentials
     */
    @Query("SELECT bc FROM BiometricCredential bc WHERE bc.expiresAt < :now AND bc.status = :status")
    List<BiometricCredential> findExpiredCredentials(
            @Param("now") LocalDateTime now, 
            @Param("status") BiometricCredential.Status status);

    /**
     * Find credentials by algorithm for migration purposes
     */
    List<BiometricCredential> findByAlgorithmAndStatus(String algorithm, BiometricCredential.Status status);

    /**
     * Find credentials with low quality scores
     */
    @Query("SELECT bc FROM BiometricCredential bc WHERE bc.qualityScore < :threshold AND bc.status = :status")
    List<BiometricCredential> findLowQualityCredentials(
            @Param("threshold") Double threshold, 
            @Param("status") BiometricCredential.Status status);

    /**
     * Find most recently used credentials for a user
     */
    @Query("SELECT bc FROM BiometricCredential bc WHERE bc.userId = :userId AND bc.status = :status ORDER BY bc.lastUsedAt DESC")
    List<BiometricCredential> findRecentlyUsedByUser(
            @Param("userId") UUID userId, 
            @Param("status") BiometricCredential.Status status);

    /**
     * Check if user has any active biometric credentials
     */
    @Query("SELECT CASE WHEN COUNT(bc) > 0 THEN true ELSE false END FROM BiometricCredential bc WHERE bc.userId = :userId AND bc.status = 'ACTIVE'")
    boolean hasActiveBiometricCredentials(@Param("userId") UUID userId);

    /**
     * Get usage statistics for a user's biometric credentials
     */
    @Query("SELECT bc.biometricType, COUNT(bc), SUM(bc.usageCount), AVG(bc.qualityScore) " +
           "FROM BiometricCredential bc " +
           "WHERE bc.userId = :userId AND bc.status = :status " +
           "GROUP BY bc.biometricType")
    List<Object[]> getBiometricUsageStats(@Param("userId") UUID userId, @Param("status") BiometricCredential.Status status);

    /**
     * Find credentials that need quality re-assessment
     */
    @Query("SELECT bc FROM BiometricCredential bc WHERE bc.qualityScore IS NULL OR " +
           "(bc.createdAt < :cutoffDate AND bc.qualityScore < :minQuality) AND bc.status = :status")
    List<BiometricCredential> findCredentialsNeedingQualityReassessment(
            @Param("cutoffDate") LocalDateTime cutoffDate,
            @Param("minQuality") Double minQuality,
            @Param("status") BiometricCredential.Status status);

    /**
     * Find duplicate credentials (same user, type, and similar quality)
     */
    @Query("SELECT bc FROM BiometricCredential bc WHERE bc.userId = :userId AND bc.biometricType = :type AND " +
           "bc.status = :status AND bc.id != :excludeId")
    List<BiometricCredential> findPotentialDuplicates(
            @Param("userId") UUID userId,
            @Param("type") BiometricType type,
            @Param("status") BiometricCredential.Status status,
            @Param("excludeId") UUID excludeId);

    /**
     * Update last used timestamp and increment usage count
     */
    @Query("UPDATE BiometricCredential bc SET bc.lastUsedAt = :timestamp, bc.usageCount = bc.usageCount + 1 " +
           "WHERE bc.credentialId = :credentialId")
    int updateUsageInfo(@Param("credentialId") String credentialId, @Param("timestamp") LocalDateTime timestamp);

    /**
     * Bulk update status for multiple credentials
     */
    @Query("UPDATE BiometricCredential bc SET bc.status = :newStatus WHERE bc.credentialId IN :credentialIds")
    int bulkUpdateStatus(@Param("credentialIds") List<String> credentialIds, @Param("newStatus") BiometricCredential.Status newStatus);

    /**
     * Delete credentials older than specified date (for cleanup)
     */
    @Query("DELETE FROM BiometricCredential bc WHERE bc.createdAt < :cutoffDate AND bc.status IN :statuses")
    int deleteOldCredentials(@Param("cutoffDate") LocalDateTime cutoffDate, @Param("statuses") List<BiometricCredential.Status> statuses);
}