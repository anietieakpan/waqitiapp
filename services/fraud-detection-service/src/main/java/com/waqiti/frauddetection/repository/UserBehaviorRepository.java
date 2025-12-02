package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.UserBehaviorPattern;
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
 * Repository for User Behavior Pattern operations
 */
@Repository
public interface UserBehaviorRepository extends JpaRepository<UserBehaviorPattern, UUID> {

    /**
     * Find behavior pattern by user ID
     */
    Optional<UserBehaviorPattern> findByUserId(UUID userId);

    /**
     * Find patterns that need updating (stale data)
     */
    @Query("SELECT ubp FROM UserBehaviorPattern ubp WHERE ubp.lastUpdated < :cutoffDate")
    List<UserBehaviorPattern> findStalePatterns(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find patterns in learning phase
     */
    List<UserBehaviorPattern> findByLearningPhaseTrue();

    /**
     * Find patterns with high fraud scores
     */
    @Query("SELECT ubp FROM UserBehaviorPattern ubp WHERE ubp.historicalFraudScore > :threshold")
    List<UserBehaviorPattern> findHighRiskPatterns(@Param("threshold") Double threshold);

    /**
     * Find patterns with many false positives
     */
    @Query("SELECT ubp FROM UserBehaviorPattern ubp WHERE ubp.falsePositiveCount > :threshold")
    List<UserBehaviorPattern> findHighFalsePositivePatterns(@Param("threshold") Integer threshold);

    /**
     * Find patterns with confirmed fraud
     */
    List<UserBehaviorPattern> findByConfirmedFraudCountGreaterThan(Integer count);

    /**
     * Update false positive count
     */
    @Modifying
    @Query("UPDATE UserBehaviorPattern ubp SET ubp.falsePositiveCount = ubp.falsePositiveCount + 1, " +
           "ubp.historicalFraudScore = CASE WHEN ubp.historicalFraudScore > 10.0 THEN ubp.historicalFraudScore - 5.0 ELSE ubp.historicalFraudScore END, " +
           "ubp.lastUpdated = :updateTime WHERE ubp.userId = :userId")
    void incrementFalsePositiveCount(@Param("userId") UUID userId, @Param("updateTime") LocalDateTime updateTime);

    /**
     * Update confirmed fraud count
     */
    @Modifying
    @Query("UPDATE UserBehaviorPattern ubp SET ubp.confirmedFraudCount = ubp.confirmedFraudCount + 1, " +
           "ubp.historicalFraudScore = CASE WHEN ubp.historicalFraudScore + 20.0 > 100.0 THEN 100.0 ELSE ubp.historicalFraudScore + 20.0 END, " +
           "ubp.lastUpdated = :updateTime WHERE ubp.userId = :userId")
    void incrementConfirmedFraudCount(@Param("userId") UUID userId, @Param("updateTime") LocalDateTime updateTime);

    /**
     * Find patterns needing confidence updates
     */
    @Query("SELECT ubp FROM UserBehaviorPattern ubp WHERE " +
           "(ubp.dataPointsCount >= 10 AND ubp.patternConfidence < 60.0) OR " +
           "(ubp.dataPointsCount >= 50 AND ubp.patternConfidence < 80.0) OR " +
           "(ubp.dataPointsCount >= 100 AND ubp.patternConfidence < 95.0)")
    List<UserBehaviorPattern> findPatternsNeedingConfidenceUpdate();

    /**
     * Get patterns for model training
     */
    @Query("SELECT ubp FROM UserBehaviorPattern ubp WHERE ubp.hasReliablePattern = true " +
           "AND ubp.dataPointsCount > :minDataPoints ORDER BY ubp.lastUpdated DESC")
    List<UserBehaviorPattern> findPatternsForTraining(@Param("minDataPoints") Integer minDataPoints);

    /**
     * Find similar patterns for anomaly detection
     */
    @Query("SELECT ubp FROM UserBehaviorPattern ubp WHERE " +
           "ubp.userId != :excludeUserId AND " +
           "ABS(ubp.typicalLatitude - :latitude) < 1.0 AND " +
           "ABS(ubp.typicalLongitude - :longitude) < 1.0 AND " +
           "ABS(ubp.typicalDailyTransactionCount - :dailyCount) <= 5")
    List<UserBehaviorPattern> findSimilarPatterns(
        @Param("excludeUserId") UUID excludeUserId,
        @Param("latitude") Double latitude,
        @Param("longitude") Double longitude,
        @Param("dailyCount") Integer dailyCount);

    /**
     * Get behavior statistics for dashboard
     */
    @Query(value = """
        SELECT 
            COUNT(*) as total_patterns,
            COUNT(CASE WHEN learning_phase = true THEN 1 END) as learning_patterns,
            AVG(pattern_confidence) as avg_confidence,
            COUNT(CASE WHEN confirmed_fraud_count > 0 THEN 1 END) as fraud_patterns,
            AVG(historical_fraud_score) as avg_fraud_score
        FROM user_behavior_patterns
        WHERE last_updated > :since
        """, nativeQuery = true)
    Object[] getBehaviorStatistics(@Param("since") LocalDateTime since);

    /**
     * Clean up old patterns for inactive users
     */
    @Modifying
    @Query("DELETE FROM UserBehaviorPattern ubp WHERE ubp.lastUpdated < :cutoffDate " +
           "AND ubp.dataPointsCount < 5")
    int cleanupInactivePatterns(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find patterns by location
     */
    @Query("SELECT ubp FROM UserBehaviorPattern ubp WHERE " +
           "ubp.typicalCountry = :country AND " +
           "(ubp.typicalCity = :city OR ubp.typicalCity IS NULL)")
    List<UserBehaviorPattern> findByLocation(@Param("country") String country, @Param("city") String city);

    /**
     * Find multi-device users
     */
    List<UserBehaviorPattern> findByMultiDeviceUserTrue();

    /**
     * Find international users
     */
    List<UserBehaviorPattern> findByInternationalUserTrue();

    /**
     * Update pattern confidence scores
     */
    @Modifying
    @Query("UPDATE UserBehaviorPattern ubp SET " +
           "ubp.patternConfidence = CASE " +
           "   WHEN ubp.dataPointsCount < 10 THEN 30.0 " +
           "   WHEN ubp.dataPointsCount < 50 THEN 60.0 " +
           "   WHEN ubp.dataPointsCount < 100 THEN 80.0 " +
           "   ELSE 95.0 END, " +
           "ubp.learningPhase = CASE WHEN ubp.dataPointsCount < 50 THEN true ELSE false END, " +
           "ubp.lastUpdated = :updateTime " +
           "WHERE ubp.id IN :patternIds")
    void updatePatternConfidences(@Param("patternIds") List<UUID> patternIds, 
                                 @Param("updateTime") LocalDateTime updateTime);
}