package com.waqiti.ml.repository;

import com.waqiti.ml.entity.UserBehaviorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Production-ready repository for User Behavior Profile operations.
 * Includes optimized queries for behavioral analysis and ML operations.
 */
@Repository
public interface UserBehaviorProfileRepository extends JpaRepository<UserBehaviorProfile, String> {

    /**
     * Find behavior profile by user ID
     */
    Optional<UserBehaviorProfile> findByUserId(String userId);

    /**
     * Find active behavior profiles
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.profileStatus = 'ACTIVE' AND ubp.deletedAt IS NULL")
    List<UserBehaviorProfile> findActiveProfiles();

    /**
     * Find high-risk profiles requiring review
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.riskScore >= :riskThreshold OR ubp.isFlaggedForReview = true")
    List<UserBehaviorProfile> findHighRiskProfiles(@Param("riskThreshold") Double riskThreshold);

    /**
     * Find profiles by risk level
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.riskLevel = :riskLevel AND ubp.deletedAt IS NULL")
    List<UserBehaviorProfile> findByRiskLevel(@Param("riskLevel") String riskLevel);

    /**
     * Find stale profiles needing analysis update
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.lastAnalysisTimestamp < :cutoffTime OR ubp.lastAnalysisTimestamp IS NULL")
    List<UserBehaviorProfile> findStaleProfiles(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find profiles with minimum transaction count
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.totalTransactionCount >= :minCount ORDER BY ubp.totalTransactionCount DESC")
    List<UserBehaviorProfile> findByMinimumTransactionCount(@Param("minCount") Long minCount);

    /**
     * Find profiles created within date range
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.createdAt BETWEEN :startDate AND :endDate")
    List<UserBehaviorProfile> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                                     @Param("endDate") LocalDateTime endDate);

    /**
     * Find profiles by confidence score range
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.confidenceScore BETWEEN :minScore AND :maxScore")
    List<UserBehaviorProfile> findByConfidenceScoreRange(@Param("minScore") Double minScore, 
                                                         @Param("maxScore") Double maxScore);

    /**
     * Count profiles by risk level
     */
    @Query("SELECT ubp.riskLevel, COUNT(ubp) FROM UserBehaviorProfile ubp WHERE ubp.deletedAt IS NULL GROUP BY ubp.riskLevel")
    List<Object[]> countByRiskLevel();

    /**
     * Find profiles needing model update
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.modelVersion != :currentVersion OR ubp.modelVersion IS NULL")
    List<UserBehaviorProfile> findProfilesNeedingModelUpdate(@Param("currentVersion") String currentVersion);

    /**
     * Update risk assessment for multiple profiles
     */
    @Modifying
    @Query("UPDATE UserBehaviorProfile ubp SET ubp.riskScore = :riskScore, ubp.riskLevel = :riskLevel, " +
           "ubp.lastAnalysisTimestamp = :timestamp WHERE ubp.userId IN :userIds")
    int updateRiskAssessmentBatch(@Param("riskScore") Double riskScore, 
                                 @Param("riskLevel") String riskLevel,
                                 @Param("timestamp") LocalDateTime timestamp,
                                 @Param("userIds") List<String> userIds);

    /**
     * Update model version for all profiles
     */
    @Modifying
    @Query("UPDATE UserBehaviorProfile ubp SET ubp.modelVersion = :modelVersion WHERE ubp.modelVersion != :modelVersion OR ubp.modelVersion IS NULL")
    int updateModelVersionForAll(@Param("modelVersion") String modelVersion);

    /**
     * Find similar profiles based on behavioral patterns
     */
    @Query(value = "SELECT ubp.* FROM user_behavior_profiles ubp " +
           "WHERE ubp.user_id != :userId " +
           "AND ubp.risk_level = :riskLevel " +
           "AND ABS(ubp.risk_score - :riskScore) <= :scoreTolerance " +
           "AND ubp.deleted_at IS NULL " +
           "ORDER BY ABS(ubp.risk_score - :riskScore) " +
           "LIMIT :limit", 
           nativeQuery = true)
    List<UserBehaviorProfile> findSimilarProfiles(@Param("userId") String userId,
                                                  @Param("riskLevel") String riskLevel,
                                                  @Param("riskScore") Double riskScore,
                                                  @Param("scoreTolerance") Double scoreTolerance,
                                                  @Param("limit") Integer limit);

    /**
     * Get risk distribution statistics
     */
    @Query("SELECT " +
           "MIN(ubp.riskScore) as minRisk, " +
           "MAX(ubp.riskScore) as maxRisk, " +
           "AVG(ubp.riskScore) as avgRisk, " +
           "COUNT(ubp) as totalProfiles " +
           "FROM UserBehaviorProfile ubp WHERE ubp.deletedAt IS NULL")
    Object getRiskDistributionStats();

    /**
     * Find profiles with compliance flags
     */
    @Query(value = "SELECT ubp.* FROM user_behavior_profiles ubp " +
           "WHERE (ubp.compliance_flags->>:flagName)::boolean = true " +
           "AND ubp.deleted_at IS NULL",
           nativeQuery = true)
    List<UserBehaviorProfile> findByComplianceFlag(@Param("flagName") String flagName);

    /**
     * Find profiles with specific ML features
     */
    @Query(value = "SELECT ubp.* FROM user_behavior_profiles ubp " +
           "WHERE ubp.ml_features ? :featureName " +
           "AND (ubp.ml_features->>:featureName)::numeric >= :minValue " +
           "AND ubp.deleted_at IS NULL",
           nativeQuery = true)
    List<UserBehaviorProfile> findByMLFeatureThreshold(@Param("featureName") String featureName,
                                                       @Param("minValue") Double minValue);

    /**
     * Get behavioral pattern summary for analytics
     */
    @Query(value = "SELECT " +
           "COUNT(CASE WHEN behavioral_patterns->'most_active_hour' IS NOT NULL THEN 1 END) as profiles_with_hour_patterns, " +
           "COUNT(CASE WHEN behavioral_patterns->'weekend_ratio' IS NOT NULL THEN 1 END) as profiles_with_weekend_patterns, " +
           "COUNT(CASE WHEN behavioral_patterns->'velocity_patterns' IS NOT NULL THEN 1 END) as profiles_with_velocity_patterns " +
           "FROM user_behavior_profiles WHERE deleted_at IS NULL",
           nativeQuery = true)
    Object getBehavioralPatternSummary();

    /**
     * Soft delete profile
     */
    @Modifying
    @Query("UPDATE UserBehaviorProfile ubp SET ubp.deletedAt = :deletedAt, ubp.profileStatus = 'DELETED' WHERE ubp.userId = :userId")
    int softDeleteProfile(@Param("userId") String userId, @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * Find profiles for periodic cleanup
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.lastUpdated < :cutoffDate AND ubp.totalTransactionCount = 0")
    List<UserBehaviorProfile> findInactiveProfilesForCleanup(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Update confidence scores based on data quality
     */
    @Modifying
    @Query("UPDATE UserBehaviorProfile ubp SET ubp.confidenceScore = " +
           "CASE " +
           "WHEN ubp.totalTransactionCount >= 100 THEN 0.9 " +
           "WHEN ubp.totalTransactionCount >= 50 THEN 0.7 " +
           "WHEN ubp.totalTransactionCount >= 20 THEN 0.5 " +
           "ELSE 0.3 END " +
           "WHERE ubp.deletedAt IS NULL")
    int updateConfidenceScoresBasedOnDataQuality();

    /**
     * Find profiles with unusual activity patterns
     */
    @Query(value = "SELECT ubp.* FROM user_behavior_profiles ubp " +
           "WHERE (ubp.ml_features->'night_transaction_ratio')::numeric > 0.5 " +
           "OR (ubp.ml_features->'weekend_transaction_ratio')::numeric > 0.7 " +
           "OR (ubp.ml_features->'velocity_anomaly_score')::numeric > 0.8 " +
           "AND ubp.deleted_at IS NULL",
           nativeQuery = true)
    List<UserBehaviorProfile> findUnusualActivityProfiles();

    /**
     * Get top risky users for monitoring
     */
    @Query("SELECT ubp FROM UserBehaviorProfile ubp WHERE ubp.deletedAt IS NULL ORDER BY ubp.riskScore DESC, ubp.lastAnalysisTimestamp DESC")
    List<UserBehaviorProfile> findTopRiskyUsers(org.springframework.data.domain.Pageable pageable);
}