package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository for ReferralLink Entity
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Repository
public interface ReferralLinkRepository extends JpaRepository<ReferralLink, UUID> {

    /**
     * Finds a link by referral code
     */
    Optional<ReferralLink> findByReferralCode(String referralCode);

    /**
     * Finds a link by short URL
     */
    Optional<ReferralLink> findByShortUrl(String shortUrl);

    /**
     * Finds a link by link ID
     */
    Optional<ReferralLink> findByLinkId(String linkId);

    /**
     * Finds all links for a user
     */
    List<ReferralLink> findByUserId(UUID userId);

    /**
     * Finds active links for a user
     */
    List<ReferralLink> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Finds all links for a program
     */
    Page<ReferralLink> findByProgram_ProgramId(String programId, Pageable pageable);

    /**
     * Finds active links for a program
     */
    List<ReferralLink> findByProgram_ProgramIdAndIsActiveTrue(String programId);

    /**
     * Finds links by user and program
     */
    List<ReferralLink> findByUserIdAndProgram_ProgramId(UUID userId, String programId);

    /**
     * Finds links by channel
     */
    List<ReferralLink> findByChannel(String channel);

    /**
     * Finds links by link type
     */
    List<ReferralLink> findByLinkType(String linkType);

    /**
     * Finds expired but still active links (for cleanup)
     */
    @Query("SELECT l FROM ReferralLink l WHERE l.isActive = true " +
           "AND l.expiresAt IS NOT NULL " +
           "AND l.expiresAt < :now")
    List<ReferralLink> findExpiredActiveLinks(@Param("now") LocalDateTime now);

    /**
     * Finds top performing links by conversion count
     */
    @Query("SELECT l FROM ReferralLink l WHERE l.program.programId = :programId " +
           "ORDER BY l.conversionCount DESC")
    Page<ReferralLink> findTopPerformingLinks(@Param("programId") String programId, Pageable pageable);

    /**
     * Finds links with high click-through but low conversion (for optimization)
     */
    @Query("SELECT l FROM ReferralLink l WHERE l.clickCount > :minClicks " +
           "AND l.conversionCount = 0 " +
           "ORDER BY l.clickCount DESC")
    List<ReferralLink> findHighClickLowConversionLinks(@Param("minClicks") int minClicks);

    /**
     * Gets total clicks for a user across all their links
     */
    @Query("SELECT COALESCE(SUM(l.clickCount), 0) FROM ReferralLink l WHERE l.userId = :userId")
    Long getTotalClicksForUser(@Param("userId") UUID userId);

    /**
     * Gets total conversions for a user across all their links
     */
    @Query("SELECT COALESCE(SUM(l.conversionCount), 0) FROM ReferralLink l WHERE l.userId = :userId")
    Long getTotalConversionsForUser(@Param("userId") UUID userId);

    /**
     * Checks if a referral code already exists
     */
    boolean existsByReferralCode(String referralCode);

    /**
     * Checks if a short URL already exists
     */
    boolean existsByShortUrl(String shortUrl);

    /**
     * Bulk deactivates expired links
     */
    @Modifying
    @Query("UPDATE ReferralLink l SET l.isActive = false WHERE l.expiresAt < :now AND l.isActive = true")
    int deactivateExpiredLinks(@Param("now") LocalDateTime now);

    /**
     * Increments click count (atomic operation)
     */
    @Modifying
    @Query("UPDATE ReferralLink l SET l.clickCount = l.clickCount + 1, " +
           "l.lastClickedAt = :clickedAt, l.updatedAt = :clickedAt " +
           "WHERE l.id = :id")
    void incrementClickCount(@Param("id") UUID id, @Param("clickedAt") LocalDateTime clickedAt);

    /**
     * Increments unique click count (atomic operation)
     */
    @Modifying
    @Query("UPDATE ReferralLink l SET l.uniqueClickCount = l.uniqueClickCount + 1, " +
           "l.updatedAt = :now WHERE l.id = :id")
    void incrementUniqueClickCount(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /**
     * Finds links by UTM parameters
     */
    @Query("SELECT l FROM ReferralLink l WHERE l.utmSource = :source " +
           "OR l.utmMedium = :medium OR l.utmCampaign = :campaign")
    List<ReferralLink> findByUtmParameters(
        @Param("source") String source,
        @Param("medium") String medium,
        @Param("campaign") String campaign
    );
}
