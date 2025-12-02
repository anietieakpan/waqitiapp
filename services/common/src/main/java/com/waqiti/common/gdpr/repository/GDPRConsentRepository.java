package com.waqiti.common.gdpr.repository;

import com.waqiti.common.gdpr.model.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GDPR Consent Repository
 *
 * Manages consent records as required by GDPR Articles 6, 7, and 13.
 * Maintains complete audit trail of all consent changes for regulatory compliance.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-19
 */
@Repository
public interface GDPRConsentRepository extends JpaRepository<ConsentRecord, UUID> {

    /**
     * Find all consent records for a user
     */
    List<ConsentRecord> findByUserId(UUID userId);

    /**
     * Find active consent records for a user
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.userId = :userId AND c.isActive = true")
    List<ConsentRecord> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Find current active consent for specific type
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.userId = :userId " +
            "AND c.consentType = :consentType " +
            "AND c.isActive = true " +
            "AND c.isGranted = true " +
            "AND (c.expiresAt IS NULL OR c.expiresAt > :now) " +
            "ORDER BY c.grantedAt DESC")
    Optional<ConsentRecord> findCurrentConsent(
            @Param("userId") UUID userId,
            @Param("consentType") ConsentRecord.ConsentType consentType,
            @Param("now") LocalDateTime now
    );

    /**
     * Find consent history for a specific type
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.userId = :userId " +
            "AND c.consentType = :consentType " +
            "ORDER BY c.createdAt DESC")
    List<ConsentRecord> findConsentHistory(
            @Param("userId") UUID userId,
            @Param("consentType") ConsentRecord.ConsentType consentType
    );

    /**
     * Find all expired consents that are still active
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.isActive = true " +
            "AND c.expiresAt IS NOT NULL " +
            "AND c.expiresAt <= :now")
    List<ConsentRecord> findExpiredConsents(@Param("now") LocalDateTime now);

    /**
     * Check if user has valid consent for a type
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM ConsentRecord c WHERE c.userId = :userId " +
            "AND c.consentType = :consentType " +
            "AND c.isActive = true " +
            "AND c.isGranted = true " +
            "AND (c.expiresAt IS NULL OR c.expiresAt > :now)")
    boolean hasValidConsent(
            @Param("userId") UUID userId,
            @Param("consentType") ConsentRecord.ConsentType consentType,
            @Param("now") LocalDateTime now
    );

    /**
     * Find all users who granted consent for a specific type
     */
    @Query("SELECT DISTINCT c.userId FROM ConsentRecord c " +
            "WHERE c.consentType = :consentType " +
            "AND c.isActive = true " +
            "AND c.isGranted = true")
    List<UUID> findUserIdsWithConsent(@Param("consentType") ConsentRecord.ConsentType consentType);

    /**
     * Count active consents by type
     */
    @Query("SELECT c.consentType, COUNT(c) FROM ConsentRecord c " +
            "WHERE c.isActive = true AND c.isGranted = true " +
            "GROUP BY c.consentType")
    List<Object[]> countActiveConsentsByType();

    /**
     * Find consents granted within a date range
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.grantedAt BETWEEN :startDate AND :endDate")
    List<ConsentRecord> findConsentsGrantedBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find consents revoked within a date range
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.revokedAt BETWEEN :startDate AND :endDate")
    List<ConsentRecord> findConsentsRevokedBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Delete all consent records for a user (for GDPR deletion)
     */
    void deleteByUserId(UUID userId);

    /**
     * Find consents by IP address (for fraud detection)
     */
    List<ConsentRecord> findByIpAddress(String ipAddress);

    /**
     * Find consents requiring renewal (expiring soon)
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.isActive = true " +
            "AND c.expiresAt IS NOT NULL " +
            "AND c.expiresAt BETWEEN :now AND :threshold")
    List<ConsentRecord> findConsentsExpiringBetween(
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold
    );
}