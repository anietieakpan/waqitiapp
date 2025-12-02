package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.LocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for tracking user location history
 * Supports impossible travel detection and geolocation-based fraud detection
 */
@Repository
public interface LocationHistoryRepository extends JpaRepository<LocationHistory, UUID> {

    /**
     * Find recent locations for user
     * Uses composite index: idx_location_history_user_timestamp
     */
    @Query("SELECT lh FROM LocationHistory lh WHERE lh.userId = :userId " +
           "AND lh.timestamp >= :since ORDER BY lh.timestamp DESC")
    List<LocationHistory> findRecentLocationsByUserId(
            @Param("userId") UUID userId,
            @Param("since") LocalDateTime since);

    /**
     * Find last known location for user
     */
    @Query("SELECT lh FROM LocationHistory lh WHERE lh.userId = :userId " +
           "ORDER BY lh.timestamp DESC LIMIT 1")
    Optional<LocationHistory> findLastLocationByUserId(@Param("userId") UUID userId);

    /**
     * Find locations by country
     */
    @Query("SELECT lh FROM LocationHistory lh WHERE lh.userId = :userId " +
           "AND lh.country = :country AND lh.timestamp >= :since")
    List<LocationHistory> findByUserIdAndCountry(
            @Param("userId") UUID userId,
            @Param("country") String country,
            @Param("since") LocalDateTime since);

    /**
     * Find suspicious location changes (impossible travel)
     * This detects locations that are geographically too far apart in too short a time
     */
    @Query(value = "SELECT * FROM location_history lh1 " +
           "WHERE lh1.user_id = :userId " +
           "AND lh1.timestamp >= :since " +
           "AND EXISTS (" +
           "  SELECT 1 FROM location_history lh2 " +
           "  WHERE lh2.user_id = lh1.user_id " +
           "  AND lh2.timestamp < lh1.timestamp " +
           "  AND lh2.timestamp >= lh1.timestamp - INTERVAL '2 hours' " +
           "  AND (lh1.country != lh2.country OR lh1.city != lh2.city)" +
           ") ORDER BY lh1.timestamp DESC", nativeQuery = true)
    List<LocationHistory> findSuspiciousLocationChanges(
            @Param("userId") UUID userId,
            @Param("since") LocalDateTime since);

    /**
     * Count distinct locations for user in time period
     */
    @Query("SELECT COUNT(DISTINCT lh.country) FROM LocationHistory lh " +
           "WHERE lh.userId = :userId AND lh.timestamp >= :since")
    long countDistinctCountries(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    /**
     * Find all locations from high-risk countries
     */
    @Query("SELECT lh FROM LocationHistory lh WHERE lh.userId = :userId " +
           "AND lh.country IN :highRiskCountries AND lh.timestamp >= :since")
    List<LocationHistory> findHighRiskCountryAccess(
            @Param("userId") UUID userId,
            @Param("highRiskCountries") List<String> highRiskCountries,
            @Param("since") LocalDateTime since);
}
