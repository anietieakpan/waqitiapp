package com.waqiti.voice.repository;

import com.waqiti.voice.domain.VoiceProfile;
import com.waqiti.voice.domain.VoiceProfile.EnrollmentStatus;
import com.waqiti.voice.domain.VoiceProfile.SecurityLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for VoiceProfile entity operations
 *
 * Critical Security Entity:
 * - Contains biometric data (special category under GDPR)
 * - All queries must enforce user ownership
 * - Deletion requests must be honored (GDPR Right to Erasure)
 * - Audit all access attempts
 */
@Repository
public interface VoiceProfileRepository extends JpaRepository<VoiceProfile, UUID> {

    // ========== Core Lookups ==========

    /**
     * Find profile by user ID (most common query)
     * Each user has exactly one voice profile
     */
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<VoiceProfile> findByUserId(UUID userId);

    /**
     * Check if user has a voice profile
     */
    boolean existsByUserId(UUID userId);

    // ========== Enrollment Queries ==========

    /**
     * Find profiles by enrollment status
     */
    List<VoiceProfile> findByEnrollmentStatus(EnrollmentStatus status);

    /**
     * Find fully enrolled profiles
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.enrollmentStatus = 'FULLY_ENROLLED' " +
           "AND vp.biometricConsentGiven = true " +
           "AND vp.dataDeletionRequested = false")
    List<VoiceProfile> findActiveEnrolledProfiles();

    /**
     * Find incomplete enrollments older than threshold (for cleanup)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.enrollmentStatus IN ('IN_PROGRESS', 'PARTIALLY_ENROLLED') " +
           "AND vp.lastEnrollmentAttemptAt < :threshold")
    List<VoiceProfile> findStaleEnrollments(@Param("threshold") LocalDateTime threshold);

    /**
     * Count profiles by enrollment status (analytics)
     */
    @Query("SELECT vp.enrollmentStatus, COUNT(vp) FROM VoiceProfile vp " +
           "GROUP BY vp.enrollmentStatus")
    List<Object[]> countByEnrollmentStatus();

    // ========== Authentication Queries ==========

    /**
     * Find profile for authentication (includes all necessary checks)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.userId = :userId " +
           "AND vp.enrollmentStatus = 'FULLY_ENROLLED' " +
           "AND vp.biometricConsentGiven = true " +
           "AND vp.dataDeletionRequested = false " +
           "AND (vp.lockedUntil IS NULL OR vp.lockedUntil < CURRENT_TIMESTAMP)")
    Optional<VoiceProfile> findForAuthentication(@Param("userId") UUID userId);

    /**
     * Find locked profiles (security monitoring)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.lockedUntil IS NOT NULL " +
           "AND vp.lockedUntil > CURRENT_TIMESTAMP " +
           "ORDER BY vp.lockedUntil DESC")
    List<VoiceProfile> findLockedProfiles();

    /**
     * Find profiles with high failure rates (fraud detection)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.consecutiveFailures >= :threshold " +
           "AND vp.lastFailedAuthAt > :sinceDate")
    List<VoiceProfile> findHighFailureProfiles(
            @Param("threshold") Integer threshold,
            @Param("sinceDate") LocalDateTime sinceDate);

    // ========== Security Level Queries ==========

    /**
     * Find profiles by security level
     */
    List<VoiceProfile> findBySecurityLevel(SecurityLevel securityLevel);

    /**
     * Count profiles by security level (analytics)
     */
    @Query("SELECT vp.securityLevel, COUNT(vp) FROM VoiceProfile vp " +
           "WHERE vp.enrollmentStatus = 'FULLY_ENROLLED' " +
           "GROUP BY vp.securityLevel")
    List<Object[]> countBySecurityLevel();

    // ========== Language & Preferences ==========

    /**
     * Find profiles by preferred language (for localization analytics)
     */
    @Query("SELECT vp.preferredLanguage, COUNT(vp) FROM VoiceProfile vp " +
           "GROUP BY vp.preferredLanguage " +
           "ORDER BY COUNT(vp) DESC")
    List<Object[]> countByPreferredLanguage();

    // ========== Statistics Queries ==========

    /**
     * Get authentication statistics for profile
     */
    @Query("SELECT vp.successfulAuthCount, vp.failedAuthCount, " +
           "vp.averageConfidenceScore FROM VoiceProfile vp " +
           "WHERE vp.userId = :userId")
    Object[] getAuthStatistics(@Param("userId") UUID userId);

    /**
     * Calculate overall authentication success rate
     */
    @Query("SELECT " +
           "SUM(vp.successfulAuthCount), " +
           "SUM(vp.failedAuthCount), " +
           "AVG(vp.averageConfidenceScore) " +
           "FROM VoiceProfile vp WHERE vp.enrollmentStatus = 'FULLY_ENROLLED'")
    Object[] getGlobalAuthStatistics();

    // ========== Consent & Compliance Queries ==========

    /**
     * Find profiles with pending consent (GDPR compliance)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.biometricConsentGiven = false " +
           "AND vp.enrollmentStatus <> 'NOT_ENROLLED'")
    List<VoiceProfile> findProfilesWithoutConsent();

    /**
     * Find profiles with data deletion requests (GDPR Right to Erasure)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.dataDeletionRequested = true " +
           "ORDER BY vp.dataDeletionScheduledAt ASC")
    List<VoiceProfile> findProfilesScheduledForDeletion();

    /**
     * Find profiles scheduled for deletion before date
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.dataDeletionRequested = true " +
           "AND vp.dataDeletionScheduledAt <= :date")
    List<VoiceProfile> findProfilesToDelete(@Param("date") LocalDateTime date);

    // ========== Activity Tracking ==========

    /**
     * Find inactive profiles (not used recently)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.lastUsedAt < :threshold " +
           "OR vp.lastUsedAt IS NULL " +
           "ORDER BY vp.lastUsedAt ASC NULLS FIRST")
    List<VoiceProfile> findInactiveProfiles(@Param("threshold") LocalDateTime threshold);

    /**
     * Find recently active profiles
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.lastUsedAt > :threshold " +
           "ORDER BY vp.lastUsedAt DESC")
    List<VoiceProfile> findRecentlyActiveProfiles(@Param("threshold") LocalDateTime threshold);

    // ========== Bulk Operations ==========

    /**
     * Unlock expired locks (scheduled job)
     */
    @Modifying
    @Query("UPDATE VoiceProfile vp SET vp.lockedUntil = NULL, vp.consecutiveFailures = 0 " +
           "WHERE vp.lockedUntil IS NOT NULL AND vp.lockedUntil < CURRENT_TIMESTAMP")
    int unlockExpiredProfiles();

    /**
     * Mark stale enrollments as expired (scheduled job)
     */
    @Modifying
    @Query("UPDATE VoiceProfile vp SET vp.enrollmentStatus = 'EXPIRED' " +
           "WHERE vp.enrollmentStatus IN ('IN_PROGRESS', 'PARTIALLY_ENROLLED') " +
           "AND vp.lastEnrollmentAttemptAt < :threshold")
    int expireStaleEnrollments(@Param("threshold") LocalDateTime threshold);

    /**
     * Delete profiles scheduled for deletion (GDPR compliance)
     * CRITICAL: Must also delete associated voice commands, sessions, etc.
     */
    @Modifying
    @Query("DELETE FROM VoiceProfile vp WHERE vp.dataDeletionRequested = true " +
           "AND vp.dataDeletionScheduledAt <= :date")
    int deleteScheduledProfiles(@Param("date") LocalDateTime date);

    // ========== Quality Metrics ==========

    /**
     * Find profiles with low average confidence (quality issues)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.averageConfidenceScore < :threshold " +
           "AND vp.successfulAuthCount > :minAttempts")
    List<VoiceProfile> findLowQualityProfiles(
            @Param("threshold") Double threshold,
            @Param("minAttempts") Long minAttempts);

    /**
     * Get confidence score distribution (analytics)
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN vp.averageConfidenceScore >= 0.9 THEN 1 END) as high, " +
           "COUNT(CASE WHEN vp.averageConfidenceScore >= 0.7 AND vp.averageConfidenceScore < 0.9 THEN 1 END) as medium, " +
           "COUNT(CASE WHEN vp.averageConfidenceScore < 0.7 THEN 1 END) as low " +
           "FROM VoiceProfile vp WHERE vp.averageConfidenceScore IS NOT NULL")
    Object[] getConfidenceDistribution();

    // ========== Version Management ==========

    /**
     * Find profiles with old signature versions (for migration)
     */
    @Query("SELECT vp FROM VoiceProfile vp WHERE vp.signatureVersion <> :currentVersion " +
           "AND vp.enrollmentStatus = 'FULLY_ENROLLED'")
    List<VoiceProfile> findProfilesWithOldSignatureVersion(@Param("currentVersion") String currentVersion);

    // ========== Existence Checks ==========

    /**
     * Check if user can authenticate (all conditions met)
     */
    @Query("SELECT CASE WHEN COUNT(vp) > 0 THEN true ELSE false END " +
           "FROM VoiceProfile vp WHERE vp.userId = :userId " +
           "AND vp.enrollmentStatus = 'FULLY_ENROLLED' " +
           "AND vp.biometricConsentGiven = true " +
           "AND vp.dataDeletionRequested = false " +
           "AND (vp.lockedUntil IS NULL OR vp.lockedUntil < CURRENT_TIMESTAMP)")
    boolean canUserAuthenticate(@Param("userId") UUID userId);
}
