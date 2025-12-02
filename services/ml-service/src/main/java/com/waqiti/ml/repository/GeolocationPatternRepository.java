package com.waqiti.ml.repository;

import com.waqiti.ml.entity.GeolocationPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Production-ready repository for GeolocationPattern operations.
 * Optimized queries for geolocation analytics and fraud detection.
 */
@Repository
public interface GeolocationPatternRepository extends JpaRepository<GeolocationPattern, String> {

    /**
     * Find the most recent location for a user
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    Optional<GeolocationPattern> findFirstByUserIdOrderByTimestampDesc(@Param("userId") String userId);

    /**
     * Find all patterns for a user
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findByUserId(@Param("userId") String userId);

    /**
     * Find patterns by user ID and time range
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.timestamp >= :startTime AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findByUserIdAndTimestampAfter(@Param("userId") String userId, 
                                                          @Param("startTime") LocalDateTime startTime);

    /**
     * Find patterns by user ID and time range with order
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.timestamp >= :startTime AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findByUserIdAndTimestampAfterOrderByTimestampDesc(@Param("userId") String userId, 
                                                                              @Param("startTime") LocalDateTime startTime);

    /**
     * Find patterns by user ID and time range
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.timestamp BETWEEN :startTime AND :endTime AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findByUserIdAndTimestampBetween(@Param("userId") String userId,
                                                            @Param("startTime") LocalDateTime startTime,
                                                            @Param("endTime") LocalDateTime endTime);

    /**
     * Find user's most frequent locations (usual locations)
     */
    @Query(value = "SELECT gp.* FROM geolocation_patterns gp " +
           "WHERE gp.user_id = :userId AND gp.deleted_at IS NULL " +
           "GROUP BY gp.user_id, gp.country, gp.city, " +
           "ROUND(gp.latitude, 2), ROUND(gp.longitude, 2) " +
           "ORDER BY COUNT(*) DESC " +
           "LIMIT :limit", 
           nativeQuery = true)
    List<GeolocationPattern> findUsualLocationsByUserId(@Param("userId") String userId, @Param("limit") Integer limit);

    /**
     * Find patterns by country
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.country = :country AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findByCountry(@Param("country") String country);

    /**
     * Find patterns by city
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.city = :city AND gp.country = :country AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findByCityAndCountry(@Param("city") String city, @Param("country") String country);

    /**
     * Find patterns within geographic radius
     */
    @Query(value = "SELECT gp.* FROM geolocation_patterns gp " +
           "WHERE gp.deleted_at IS NULL " +
           "AND (6371 * acos(cos(radians(:latitude)) * cos(radians(gp.latitude)) * " +
           "cos(radians(gp.longitude) - radians(:longitude)) + sin(radians(:latitude)) * " +
           "sin(radians(gp.latitude)))) <= :radiusKm " +
           "ORDER BY (6371 * acos(cos(radians(:latitude)) * cos(radians(gp.latitude)) * " +
           "cos(radians(gp.longitude) - radians(:longitude)) + sin(radians(:latitude)) * " +
           "sin(radians(gp.latitude))))",
           nativeQuery = true)
    List<GeolocationPattern> findWithinRadius(@Param("latitude") Double latitude,
                                             @Param("longitude") Double longitude,
                                             @Param("radiusKm") Double radiusKm);

    /**
     * Find high-risk locations
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.riskScore >= :riskThreshold AND gp.deletedAt IS NULL ORDER BY gp.riskScore DESC")
    List<GeolocationPattern> findHighRiskLocations(@Param("riskThreshold") Double riskThreshold);

    /**
     * Find locations with velocity violations
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.velocityImpossible = true AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findVelocityViolations();

    /**
     * Find locations flagged for review
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.flaggedForReview = true AND gp.deletedAt IS NULL ORDER BY gp.riskScore DESC")
    List<GeolocationPattern> findFlaggedForReview();

    /**
     * Find mock locations
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.mockLocationDetected = true AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findMockLocations();

    /**
     * Find fraud hotspot locations
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.fraudHotspotDetected = true AND gp.deletedAt IS NULL ORDER BY gp.riskScore DESC")
    List<GeolocationPattern> findFraudHotspots();

    /**
     * Count patterns by user and country
     */
    @Query("SELECT COUNT(gp) FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.country = :country AND gp.deletedAt IS NULL")
    Long countByUserIdAndCountry(@Param("userId") String userId, @Param("country") String country);

    /**
     * Count patterns by user and city
     */
    @Query("SELECT COUNT(gp) FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.city = :city AND gp.country = :country AND gp.deletedAt IS NULL")
    Long countByUserIdAndCityAndCountry(@Param("userId") String userId, @Param("city") String city, @Param("country") String country);

    /**
     * Find frequent travel routes for user
     */
    @Query(value = "WITH location_pairs AS (" +
           "  SELECT " +
           "    lag(country) OVER (PARTITION BY user_id ORDER BY timestamp) as from_country," +
           "    lag(city) OVER (PARTITION BY user_id ORDER BY timestamp) as from_city," +
           "    country as to_country," +
           "    city as to_city," +
           "    user_id" +
           "  FROM geolocation_patterns " +
           "  WHERE user_id = :userId AND deleted_at IS NULL" +
           ") " +
           "SELECT from_country, from_city, to_country, to_city, COUNT(*) as frequency " +
           "FROM location_pairs " +
           "WHERE from_country IS NOT NULL AND to_country IS NOT NULL " +
           "AND (from_country != to_country OR from_city != to_city) " +
           "GROUP BY from_country, from_city, to_country, to_city " +
           "HAVING COUNT(*) >= :minFrequency " +
           "ORDER BY COUNT(*) DESC",
           nativeQuery = true)
    List<Object[]> findFrequentTravelRoutes(@Param("userId") String userId, @Param("minFrequency") Integer minFrequency);

    /**
     * Get location statistics for user
     */
    @Query("SELECT " +
           "COUNT(gp) as totalLocations, " +
           "COUNT(DISTINCT gp.country) as uniqueCountries, " +
           "COUNT(DISTINCT gp.city) as uniqueCities, " +
           "AVG(gp.riskScore) as averageRiskScore, " +
           "MAX(gp.riskScore) as maxRiskScore, " +
           "COUNT(CASE WHEN gp.velocityImpossible = true THEN 1 END) as velocityViolations, " +
           "COUNT(CASE WHEN gp.mockLocationDetected = true THEN 1 END) as mockLocations " +
           "FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.deletedAt IS NULL")
    Object getLocationStatistics(@Param("userId") String userId);

    /**
     * Find locations with poor accuracy
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.accuracyMeters > :accuracyThreshold AND gp.deletedAt IS NULL ORDER BY gp.accuracyMeters DESC")
    List<GeolocationPattern> findPoorAccuracyLocations(@Param("accuracyThreshold") Double accuracyThreshold);

    /**
     * Find patterns by cluster ID
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.clusterId = :clusterId AND gp.deletedAt IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findByClusterId(@Param("clusterId") String clusterId);

    /**
     * Find unprocessed patterns by ML
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.processedByMl = false OR gp.mlModelVersion IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findUnprocessedByML();

    /**
     * Update ML processing status for multiple patterns
     */
    @Modifying
    @Query("UPDATE GeolocationPattern gp SET gp.processedByMl = true, gp.mlModelVersion = :modelVersion WHERE gp.id IN :patternIds")
    int markAsProcessedByML(@Param("modelVersion") String modelVersion, @Param("patternIds") List<String> patternIds);

    /**
     * Find patterns needing reprocessing due to model update
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.mlModelVersion != :currentVersion OR gp.mlModelVersion IS NULL ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findPatternsNeedingReprocessing(@Param("currentVersion") String currentVersion);

    /**
     * Get hourly distribution of locations for user
     */
    @Query("SELECT EXTRACT(HOUR FROM gp.timestamp) as hour, COUNT(gp) as count " +
           "FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.deletedAt IS NULL " +
           "GROUP BY EXTRACT(HOUR FROM gp.timestamp) ORDER BY hour")
    List<Object[]> getHourlyLocationDistribution(@Param("userId") String userId);

    /**
     * Get daily distribution of locations for user
     */
    @Query("SELECT EXTRACT(DOW FROM gp.timestamp) as dayOfWeek, COUNT(gp) as count " +
           "FROM GeolocationPattern gp WHERE gp.userId = :userId AND gp.deletedAt IS NULL " +
           "GROUP BY EXTRACT(DOW FROM gp.timestamp) ORDER BY dayOfWeek")
    List<Object[]> getDailyLocationDistribution(@Param("userId") String userId);

    /**
     * Find location anomalies for investigation
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE " +
           "(gp.geographicAnomalyScore >= :anomalyThreshold OR " +
           "gp.velocityImpossible = true OR " +
           "gp.mockLocationDetected = true OR " +
           "gp.fraudHotspotDetected = true) AND " +
           "gp.deletedAt IS NULL " +
           "ORDER BY gp.riskScore DESC, gp.timestamp DESC")
    List<GeolocationPattern> findLocationAnomalies(@Param("anomalyThreshold") Double anomalyThreshold);

    /**
     * Find patterns by transaction ID
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE gp.transactionId = :transactionId AND gp.deletedAt IS NULL")
    Optional<GeolocationPattern> findByTransactionId(@Param("transactionId") String transactionId);

    /**
     * Bulk update risk scores
     */
    @Modifying
    @Query("UPDATE GeolocationPattern gp SET gp.riskScore = :riskScore, gp.riskLevel = :riskLevel WHERE gp.id IN :patternIds")
    int updateRiskScores(@Param("riskScore") Double riskScore, 
                        @Param("riskLevel") String riskLevel, 
                        @Param("patternIds") List<String> patternIds);

    /**
     * Soft delete old patterns for cleanup
     */
    @Modifying
    @Query("UPDATE GeolocationPattern gp SET gp.deletedAt = :deletedAt WHERE gp.timestamp < :cutoffDate AND gp.deletedAt IS NULL")
    int softDeleteOldPatterns(@Param("cutoffDate") LocalDateTime cutoffDate, @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * Hard delete very old patterns
     */
    @Modifying
    @Query("DELETE FROM GeolocationPattern gp WHERE gp.deletedAt IS NOT NULL AND gp.deletedAt < :cutoffDate")
    int hardDeleteOldPatterns(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find travel patterns between countries
     */
    @Query(value = "SELECT " +
           "from_country, to_country, COUNT(*) as frequency, " +
           "AVG(travel_time_hours) as avg_travel_time, " +
           "AVG(distance_km) as avg_distance " +
           "FROM (" +
           "  SELECT " +
           "    lag(country) OVER (PARTITION BY user_id ORDER BY timestamp) as from_country," +
           "    country as to_country," +
           "    EXTRACT(EPOCH FROM (timestamp - lag(timestamp) OVER (PARTITION BY user_id ORDER BY timestamp)))/3600 as travel_time_hours," +
           "    (6371 * acos(cos(radians(lag(latitude) OVER (PARTITION BY user_id ORDER BY timestamp))) * " +
           "     cos(radians(latitude)) * cos(radians(longitude) - radians(lag(longitude) OVER (PARTITION BY user_id ORDER BY timestamp))) + " +
           "     sin(radians(lag(latitude) OVER (PARTITION BY user_id ORDER BY timestamp))) * sin(radians(latitude)))) as distance_km " +
           "  FROM geolocation_patterns " +
           "  WHERE user_id = :userId AND deleted_at IS NULL" +
           ") travel_data " +
           "WHERE from_country IS NOT NULL AND to_country IS NOT NULL " +
           "AND from_country != to_country " +
           "GROUP BY from_country, to_country " +
           "HAVING COUNT(*) >= :minFrequency " +
           "ORDER BY COUNT(*) DESC",
           nativeQuery = true)
    List<Object[]> findInternationalTravelPatterns(@Param("userId") String userId, @Param("minFrequency") Integer minFrequency);

    /**
     * Find concurrent locations (same user, multiple locations at similar times)
     */
    @Query(value = "SELECT gp1.*, gp2.* FROM geolocation_patterns gp1 " +
           "JOIN geolocation_patterns gp2 ON gp1.user_id = gp2.user_id " +
           "WHERE gp1.user_id = :userId " +
           "AND gp1.id != gp2.id " +
           "AND ABS(EXTRACT(EPOCH FROM (gp1.timestamp - gp2.timestamp))/60) <= :timeWindowMinutes " +
           "AND (6371 * acos(cos(radians(gp1.latitude)) * cos(radians(gp2.latitude)) * " +
           "cos(radians(gp2.longitude) - radians(gp1.longitude)) + " +
           "sin(radians(gp1.latitude)) * sin(radians(gp2.latitude)))) > :minDistanceKm " +
           "AND gp1.deleted_at IS NULL AND gp2.deleted_at IS NULL " +
           "ORDER BY gp1.timestamp DESC",
           nativeQuery = true)
    List<Object[]> findConcurrentLocationAnomalies(@Param("userId") String userId, 
                                                  @Param("timeWindowMinutes") Integer timeWindowMinutes,
                                                  @Param("minDistanceKm") Double minDistanceKm);

    /**
     * Get location clustering data for ML analysis
     */
    @Query(value = "SELECT " +
           "ROUND(latitude, 3) as lat_cluster, " +
           "ROUND(longitude, 3) as lon_cluster, " +
           "country, city, " +
           "COUNT(*) as frequency, " +
           "AVG(risk_score) as avg_risk_score, " +
           "MIN(timestamp) as first_seen, " +
           "MAX(timestamp) as last_seen " +
           "FROM geolocation_patterns " +
           "WHERE user_id = :userId AND deleted_at IS NULL " +
           "GROUP BY ROUND(latitude, 3), ROUND(longitude, 3), country, city " +
           "HAVING COUNT(*) >= :minFrequency " +
           "ORDER BY COUNT(*) DESC",
           nativeQuery = true)
    List<Object[]> getLocationClusters(@Param("userId") String userId, @Param("minFrequency") Integer minFrequency);

    /**
     * Update cluster assignments
     */
    @Modifying
    @Query("UPDATE GeolocationPattern gp SET gp.clusterId = :clusterId, gp.clusterDistance = :clusterDistance WHERE gp.id IN :patternIds")
    int updateClusterAssignments(@Param("clusterId") String clusterId, 
                                @Param("clusterDistance") Double clusterDistance, 
                                @Param("patternIds") List<String> patternIds);

    /**
     * Find patterns for ML training data
     */
    @Query("SELECT gp FROM GeolocationPattern gp WHERE " +
           "gp.processedByMl = true AND " +
           "gp.riskScore IS NOT NULL AND " +
           "gp.confidenceScore >= :minConfidence AND " +
           "gp.deletedAt IS NULL " +
           "ORDER BY gp.timestamp DESC")
    List<GeolocationPattern> findMLTrainingData(@Param("minConfidence") Double minConfidence);

    /**
     * Get global location statistics for analytics
     */
    @Query("SELECT " +
           "COUNT(gp) as totalPatterns, " +
           "COUNT(DISTINCT gp.userId) as uniqueUsers, " +
           "COUNT(DISTINCT gp.country) as uniqueCountries, " +
           "COUNT(DISTINCT gp.city) as uniqueCities, " +
           "AVG(gp.riskScore) as averageRiskScore, " +
           "COUNT(CASE WHEN gp.velocityImpossible = true THEN 1 END) as velocityViolations, " +
           "COUNT(CASE WHEN gp.mockLocationDetected = true THEN 1 END) as mockLocations, " +
           "COUNT(CASE WHEN gp.fraudHotspotDetected = true THEN 1 END) as fraudHotspots " +
           "FROM GeolocationPattern gp WHERE gp.deletedAt IS NULL")
    Object getGlobalLocationStatistics();

    /**
     * Find top risky locations globally
     */
    @Query(value = "SELECT " +
           "country, city, " +
           "COUNT(*) as transaction_count, " +
           "AVG(risk_score) as avg_risk_score, " +
           "COUNT(CASE WHEN velocity_impossible = true THEN 1 END) as velocity_violations, " +
           "COUNT(CASE WHEN mock_location_detected = true THEN 1 END) as mock_locations " +
           "FROM geolocation_patterns " +
           "WHERE deleted_at IS NULL AND risk_score >= :riskThreshold " +
           "GROUP BY country, city " +
           "HAVING COUNT(*) >= :minTransactions " +
           "ORDER BY AVG(risk_score) DESC, COUNT(*) DESC " +
           "LIMIT :limit",
           nativeQuery = true)
    List<Object[]> findTopRiskyLocationsGlobally(@Param("riskThreshold") Double riskThreshold, 
                                                @Param("minTransactions") Integer minTransactions,
                                                @Param("limit") Integer limit);

    /**
     * Find location outliers for anomaly detection
     */
    @Query(value = "SELECT gp.* FROM geolocation_patterns gp " +
           "WHERE gp.user_id = :userId " +
           "AND gp.deleted_at IS NULL " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM geolocation_patterns gp2 " +
           "  WHERE gp2.user_id = gp.user_id " +
           "  AND gp2.id != gp.id " +
           "  AND gp2.deleted_at IS NULL " +
           "  AND (6371 * acos(cos(radians(gp.latitude)) * cos(radians(gp2.latitude)) * " +
           "  cos(radians(gp2.longitude) - radians(gp.longitude)) + " +
           "  sin(radians(gp.latitude)) * sin(radians(gp2.latitude)))) <= :radiusKm" +
           ") " +
           "ORDER BY gp.risk_score DESC",
           nativeQuery = true)
    List<GeolocationPattern> findLocationOutliers(@Param("userId") String userId, @Param("radiusKm") Double radiusKm);
}