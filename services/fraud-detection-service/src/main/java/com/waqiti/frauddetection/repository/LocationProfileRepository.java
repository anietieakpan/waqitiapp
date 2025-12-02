package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.LocationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Location Profile Repository
 *
 * Data access layer for location profiles with optimized queries for
 * geographic fraud detection, VPN/proxy detection, and IP reputation management.
 *
 * PRODUCTION-GRADE QUERIES
 * - Indexed lookups for performance
 * - VPN/Proxy detection queries
 * - High-risk jurisdiction filtering
 * - Geographic anomaly detection support
 * - IP reputation tracking
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Repository
public interface LocationProfileRepository extends JpaRepository<LocationProfile, UUID> {

    /**
     * Find location profile by IP address
     * Uses unique index on ip_address for O(log n) lookup
     */
    Optional<LocationProfile> findByIpAddress(String ipAddress);

    /**
     * Find locations by risk level
     * Uses index on current_risk_level
     */
    List<LocationProfile> findByCurrentRiskLevel(String riskLevel);

    /**
     * Find high-risk locations seen recently
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.currentRiskLevel = 'HIGH' AND lp.lastSeenDate >= :since")
    List<LocationProfile> findHighRiskLocationsSince(@Param("since") LocalDateTime since);

    /**
     * Find critical risk locations (blacklisted, high fraud)
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.currentRiskLevel = 'CRITICAL' OR lp.isBlacklisted = true ORDER BY lp.lastSeenDate DESC")
    List<LocationProfile> findCriticalRiskLocations();

    /**
     * Find VPN/Proxy IPs
     * Uses index on is_vpn_or_proxy
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.isVpnOrProxy = true AND lp.lastSeenDate >= :since")
    List<LocationProfile> findVpnOrProxyLocations(@Param("since") LocalDateTime since);

    /**
     * Find locations from high-risk countries
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.countryCode IN :countryCodes")
    List<LocationProfile> findLocationsByCountryCodes(@Param("countryCodes") List<String> countryCodes);

    /**
     * Find locations with high fraud rates
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.fraudRate > :threshold ORDER BY lp.fraudRate DESC")
    List<LocationProfile> findLocationsWithHighFraudRate(@Param("threshold") double threshold);

    /**
     * Find locations associated with specific user
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE :userId MEMBER OF lp.associatedUserIds ORDER BY lp.lastSeenDate DESC")
    List<LocationProfile> findLocationsByUserId(@Param("userId") UUID userId);

    /**
     * Find recent locations for user (for country hopping detection)
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE :userId MEMBER OF lp.associatedUserIds AND lp.lastSeenDate >= :since ORDER BY lp.lastSeenDate DESC")
    List<LocationProfile> findRecentLocationsByUserId(
        @Param("userId") UUID userId,
        @Param("since") LocalDateTime since
    );

    /**
     * Find locations from specific country
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.countryCode = :countryCode")
    List<LocationProfile> findLocationsByCountry(@Param("countryCode") String countryCode);

    /**
     * Find blacklisted IPs
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.isBlacklisted = true ORDER BY lp.blacklistDate DESC")
    List<LocationProfile> findBlacklistedIps();

    /**
     * Find IPs by ASN (for network-level fraud detection)
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.asn = :asn")
    List<LocationProfile> findLocationsByAsn(@Param("asn") String asn);

    /**
     * Find IPs by ISP
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.isp = :isp")
    List<LocationProfile> findLocationsByIsp(@Param("isp") String isp);

    /**
     * Count locations by country (for analytics)
     */
    @Query("SELECT lp.countryCode, COUNT(lp) FROM LocationProfile lp GROUP BY lp.countryCode ORDER BY COUNT(lp) DESC")
    List<Object[]> countLocationsByCountry();

    /**
     * Count locations by risk level
     */
    @Query("SELECT lp.currentRiskLevel, COUNT(lp) FROM LocationProfile lp GROUP BY lp.currentRiskLevel")
    List<Object[]> countLocationsByRiskLevel();

    /**
     * Find shared IPs (many users from same IP)
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE SIZE(lp.associatedUserIds) >= :threshold ORDER BY SIZE(lp.associatedUserIds) DESC")
    List<LocationProfile> findSharedIps(@Param("threshold") int threshold);

    /**
     * Find new IPs with high activity (suspicious)
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.firstSeenDate >= :since AND lp.totalTransactions > :txThreshold")
    List<LocationProfile> findNewIpsWithHighActivity(
        @Param("since") LocalDateTime since,
        @Param("txThreshold") long txThreshold
    );

    /**
     * Check if IP address exists
     */
    boolean existsByIpAddress(String ipAddress);

    /**
     * Find recently active locations
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.lastSeenDate >= :since ORDER BY lp.lastSeenDate DESC")
    List<LocationProfile> findRecentlyActiveLocations(@Param("since") LocalDateTime since);

    /**
     * Find locations with recent fraud activity
     */
    @Query("SELECT lp FROM LocationProfile lp WHERE lp.lastFraudDate >= :since ORDER BY lp.lastFraudDate DESC")
    List<LocationProfile> findLocationsWithRecentFraud(@Param("since") LocalDateTime since);

    /**
     * Find locations within geographic radius (for proximity analysis)
     * Uses Haversine formula for distance calculation
     */
    @Query(value = "SELECT * FROM location_profiles lp WHERE " +
           "(6371 * acos(cos(radians(:latitude)) * cos(radians(lp.latitude)) * " +
           "cos(radians(lp.longitude) - radians(:longitude)) + " +
           "sin(radians(:latitude)) * sin(radians(lp.latitude)))) < :radiusKm",
           nativeQuery = true)
    List<LocationProfile> findLocationsWithinRadius(
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusKm") double radiusKm
    );
}
