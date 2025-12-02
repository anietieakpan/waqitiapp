package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralClick;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ReferralClick Entity
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Repository
public interface ReferralClickRepository extends JpaRepository<ReferralClick, UUID> {

    /**
     * Finds a click by click ID
     */
    Optional<ReferralClick> findByClickId(String clickId);

    /**
     * Finds all clicks for a link
     */
    Page<ReferralClick> findByLinkId(String linkId, Pageable pageable);

    /**
     * Finds all clicks for a referral code
     */
    Page<ReferralClick> findByReferralCode(String referralCode, Pageable pageable);

    /**
     * Finds clicks by session ID
     */
    List<ReferralClick> findBySessionId(String sessionId);

    /**
     * Finds clicks by IP address
     */
    List<ReferralClick> findByIpAddress(String ipAddress);

    /**
     * Finds converted clicks for a link
     */
    List<ReferralClick> findByLinkIdAndConvertedTrue(String linkId);

    /**
     * Finds clicks from a specific country
     */
    Page<ReferralClick> findByCountryCode(String countryCode, Pageable pageable);

    /**
     * Finds clicks by device type
     */
    List<ReferralClick> findByDeviceType(String deviceType);

    /**
     * Finds bot clicks (for cleanup/analysis)
     */
    List<ReferralClick> findByIsBotTrue();

    /**
     * Finds unique clicks only
     */
    List<ReferralClick> findByIsUniqueClickTrue();

    /**
     * Finds clicks within a time range
     */
    @Query("SELECT c FROM ReferralClick c WHERE c.clickedAt BETWEEN :startDate AND :endDate")
    List<ReferralClick> findClicksBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Finds clicks from a specific IP within a time window (fraud detection)
     */
    @Query("SELECT c FROM ReferralClick c WHERE c.ipAddress = :ipAddress " +
           "AND c.clickedAt >= :since")
    List<ReferralClick> findRecentClicksByIp(
        @Param("ipAddress") String ipAddress,
        @Param("since") LocalDateTime since
    );

    /**
     * Counts clicks for a link within a time period
     */
    @Query("SELECT COUNT(c) FROM ReferralClick c WHERE c.linkId = :linkId " +
           "AND c.clickedAt BETWEEN :startDate AND :endDate")
    Long countClicksForLinkInPeriod(
        @Param("linkId") String linkId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Counts unique clicks (by IP) for a link
     */
    @Query("SELECT COUNT(DISTINCT c.ipAddress) FROM ReferralClick c WHERE c.linkId = :linkId")
    Long countUniqueClicksForLink(@Param("linkId") String linkId);

    /**
     * Gets conversion rate for a link
     */
    @Query("SELECT CAST(SUM(CASE WHEN c.converted = true THEN 1 ELSE 0 END) AS double) / " +
           "CAST(COUNT(c) AS double) " +
           "FROM ReferralClick c WHERE c.linkId = :linkId")
    Double getConversionRateForLink(@Param("linkId") String linkId);

    /**
     * Gets average time to conversion in hours
     */
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (c.convertedAt - c.clickedAt)) / 3600.0) " +
           "FROM ReferralClick c WHERE c.converted = true AND c.linkId = :linkId")
    Double getAverageTimeToConversionHours(@Param("linkId") String linkId);

    /**
     * Finds clicks by country grouped (for analytics)
     */
    @Query("SELECT c.countryCode, COUNT(c) FROM ReferralClick c " +
           "WHERE c.linkId = :linkId GROUP BY c.countryCode " +
           "ORDER BY COUNT(c) DESC")
    List<Object[]> getClicksByCountry(@Param("linkId") String linkId);

    /**
     * Finds clicks by device type grouped
     */
    @Query("SELECT c.deviceType, COUNT(c) FROM ReferralClick c " +
           "WHERE c.linkId = :linkId GROUP BY c.deviceType")
    List<Object[]> getClicksByDeviceType(@Param("linkId") String linkId);

    /**
     * Finds suspicious click patterns (multiple clicks from same IP in short time)
     */
    @Query("SELECT c.ipAddress, COUNT(c) as clickCount FROM ReferralClick c " +
           "WHERE c.clickedAt >= :since " +
           "GROUP BY c.ipAddress " +
           "HAVING COUNT(c) > :threshold " +
           "ORDER BY clickCount DESC")
    List<Object[]> findSuspiciousClickPatterns(
        @Param("since") LocalDateTime since,
        @Param("threshold") long threshold
    );

    /**
     * Checks if IP has clicked within timeframe (for duplicate detection)
     */
    boolean existsByIpAddressAndLinkIdAndClickedAtAfter(
        String ipAddress,
        String linkId,
        LocalDateTime since
    );

    /**
     * Gets total clicks for a referral code
     */
    @Query("SELECT COUNT(c) FROM ReferralClick c WHERE c.referralCode = :code")
    Long getTotalClicksForCode(@Param("code") String code);

    /**
     * Gets conversion count for a referral code
     */
    @Query("SELECT COUNT(c) FROM ReferralClick c WHERE c.referralCode = :code AND c.converted = true")
    Long getConversionCountForCode(@Param("code") String code);
}
