package com.waqiti.risk.repository;

import com.waqiti.risk.dto.VelocityMetrics;
import com.waqiti.risk.model.VelocityData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Velocity Repository
 *
 * MongoDB repository for managing velocity data
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface VelocityRepository extends MongoRepository<VelocityData, String> {

    /**
     * Find velocity data by user ID
     */
    List<VelocityData> findByUserId(String userId);

    /**
     * Find recent velocity data for user
     */
    @Query("{'userId': ?0, 'windowStart': {'$gte': ?1}}")
    List<VelocityData> findRecentByUserId(String userId, LocalDateTime since);

    /**
     * Get velocity metrics for user within window
     */
    @Query("{'userId': ?0, 'windowStart': {'$gte': ?1}}")
    VelocityMetrics getVelocityMetrics(String userId, LocalDateTime windowStart);

    /**
     * Find velocity data by merchant
     */
    List<VelocityData> findByMerchantId(String merchantId);

    /**
     * Find velocity data by device
     */
    List<VelocityData> findByDeviceId(String deviceId);

    /**
     * Find velocity data by IP address
     */
    List<VelocityData> findByIpAddress(String ipAddress);

    /**
     * Find velocity data by window type
     */
    List<VelocityData> findByWindowType(String windowType);

    /**
     * Find active velocity windows
     */
    @Query("{'windowStart': {'$gte': ?0}, 'windowEnd': {'$lte': ?1}}")
    List<VelocityData> findActiveWindows(LocalDateTime start, LocalDateTime end);

    /**
     * Find velocity data with high scores
     */
    @Query("{'velocityScore': {'$gte': ?0}}")
    List<VelocityData> findHighVelocityData(Double scoreThreshold);

    /**
     * Find velocity data exceeding thresholds
     */
    @Query("{'exceedsThreshold': true}")
    List<VelocityData> findExceedingThresholds();

    /**
     * Find card testing patterns
     */
    @Query("{'cardTestingPatternDetected': true}")
    List<VelocityData> findCardTestingPatterns();

    /**
     * Find rapid fire patterns
     */
    @Query("{'rapidFireDetected': true}")
    List<VelocityData> findRapidFirePatterns();

    /**
     * Find unusual patterns
     */
    @Query("{'unusualPatternDetected': true}")
    List<VelocityData> findUnusualPatterns();

    /**
     * Find suspicious velocity data
     */
    @Query("{'$or': [" +
           "{'velocityScore': {'$gte': ?0}}, " +
           "{'cardTestingPatternDetected': true}, " +
           "{'rapidFireDetected': true}, " +
           "{'exceedsThreshold': true}" +
           "]}")
    List<VelocityData> findSuspiciousVelocity(Double scoreThreshold);

    /**
     * Find multi-country velocity
     */
    @Query("{'multipleCountries': true, 'distinctCountries': {'$gte': ?0}}")
    List<VelocityData> findMultiCountryVelocity(Integer minCountries);

    /**
     * Find multi-device velocity
     */
    @Query("{'multipleDevices': true}")
    List<VelocityData> findMultiDeviceVelocity();

    /**
     * Find high failure rate velocity
     */
    @Query("{'highFailureRate': true}")
    List<VelocityData> findHighFailureRateVelocity();

    /**
     * Find velocity by transaction count
     */
    @Query("{'transactionCount': {'$gte': ?0}}")
    List<VelocityData> findByTransactionCountGreaterThan(Integer count);

    /**
     * Find velocity by unique merchants
     */
    @Query("{'uniqueMerchants': {'$gte': ?0}}")
    List<VelocityData> findByUniqueMerchantsGreaterThan(Integer count);

    /**
     * Find velocity data for user and merchant
     */
    @Query("{'userId': ?0, 'merchantId': ?1, 'windowStart': {'$gte': ?2}}")
    List<VelocityData> findByUserAndMerchant(String userId, String merchantId, LocalDateTime since);

    /**
     * Find hourly velocity windows
     */
    @Query("{'windowType': 'HOUR', 'windowStart': {'$gte': ?0}}")
    List<VelocityData> findHourlyWindows(LocalDateTime since);

    /**
     * Find daily velocity windows
     */
    @Query("{'windowType': 'DAY', 'windowStart': {'$gte': ?0}}")
    List<VelocityData> findDailyWindows(LocalDateTime since);

    /**
     * Find velocity data with specific pattern
     */
    @Query("{'detectedPatterns': {'$in': [?0]}}")
    List<VelocityData> findByDetectedPattern(String pattern);

    /**
     * Find velocity data by session
     */
    Optional<VelocityData> findBySessionId(String sessionId);

    /**
     * Find stale velocity data (for cleanup)
     */
    @Query("{'windowEnd': {'$lt': ?0}}")
    List<VelocityData> findStaleVelocityData(LocalDateTime before);

    /**
     * Count velocity windows for user
     */
    long countByUserIdAndWindowStartGreaterThan(String userId, LocalDateTime since);

    /**
     * Find velocity data created between dates
     */
    List<VelocityData> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
