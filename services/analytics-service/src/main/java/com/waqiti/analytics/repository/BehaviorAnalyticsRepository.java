package com.waqiti.analytics.repository;

import com.waqiti.analytics.entity.BehaviorAnalytics;
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
 * Behavior analytics repository with advanced behavioral analysis queries
 */
@Repository
public interface BehaviorAnalyticsRepository extends JpaRepository<BehaviorAnalytics, UUID> {
    
    /**
     * Find behavior analytics by user
     */
    Page<BehaviorAnalytics> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * Find behavior by session
     */
    List<BehaviorAnalytics> findBySessionIdOrderByActivityDate(String sessionId);
    
    /**
     * Find user activities in date range
     */
    List<BehaviorAnalytics> findByUserIdAndActivityDateBetween(
        UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find anomalous behaviors
     */
    @Query("SELECT ba FROM BehaviorAnalytics ba WHERE ba.isAnomaly = true " +
           "AND ba.userId = :userId " +
           "AND ba.activityDate >= :sinceDate " +
           "ORDER BY ba.anomalyScore DESC")
    List<BehaviorAnalytics> findAnomalousBehaviors(
        @Param("userId") UUID userId,
        @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Calculate average engagement score
     */
    @Query("SELECT AVG(ba.engagementScore) FROM BehaviorAnalytics ba " +
           "WHERE ba.userId = :userId " +
           "AND ba.activityDate BETWEEN :startDate AND :endDate")
    Optional<Double> calculateAverageEngagementScore(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find high-risk behaviors
     */
    List<BehaviorAnalytics> findByUserIdAndRiskScoreGreaterThan(
        UUID userId, Double riskThreshold);
    
    /**
     * Get user activity patterns
     */
    @Query("SELECT ba.activityType, COUNT(ba), AVG(ba.sessionDurationSeconds) " +
           "FROM BehaviorAnalytics ba " +
           "WHERE ba.userId = :userId " +
           "AND ba.activityDate >= :sinceDate " +
           "GROUP BY ba.activityType")
    List<Object[]> getUserActivityPatterns(
        @Param("userId") UUID userId,
        @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Find suspicious login patterns
     */
    @Query("SELECT ba FROM BehaviorAnalytics ba " +
           "WHERE ba.userId = :userId " +
           "AND ba.activityType = 'LOGIN' " +
           "AND (ba.behaviorType = 'SUSPICIOUS' OR ba.behaviorType = 'FRAUDULENT') " +
           "ORDER BY ba.activityDate DESC")
    List<BehaviorAnalytics> findSuspiciousLoginPatterns(@Param("userId") UUID userId);
    
    /**
     * Get device usage statistics
     */
    @Query("SELECT ba.deviceType, ba.platform, COUNT(ba) " +
           "FROM BehaviorAnalytics ba " +
           "WHERE ba.userId = :userId " +
           "GROUP BY ba.deviceType, ba.platform")
    List<Object[]> getDeviceUsageStats(@Param("userId") UUID userId);
    
    /**
     * Find behaviors by IP address
     */
    List<BehaviorAnalytics> findByIpAddressAndActivityDateAfter(
        String ipAddress, LocalDateTime afterDate);
    
    /**
     * Get feature usage analytics
     */
    @Query("SELECT ba.mostUsedFeature, COUNT(ba), SUM(ba.featureInteractionCount) " +
           "FROM BehaviorAnalytics ba " +
           "WHERE ba.userId = :userId " +
           "AND ba.activityDate >= :sinceDate " +
           "AND ba.mostUsedFeature IS NOT NULL " +
           "GROUP BY ba.mostUsedFeature " +
           "ORDER BY SUM(ba.featureInteractionCount) DESC")
    List<Object[]> getFeatureUsageAnalytics(
        @Param("userId") UUID userId,
        @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Update behavioral patterns
     */
    @Modifying
    @Query("UPDATE BehaviorAnalytics ba SET " +
           "ba.engagementScore = :engagementScore, " +
           "ba.riskScore = :riskScore, " +
           "ba.isAnomaly = :isAnomaly, " +
           "ba.anomalyReasons = :anomalyReasons " +
           "WHERE ba.id = :behaviorId")
    void updateBehavioralPatterns(
        @Param("behaviorId") UUID behaviorId,
        @Param("engagementScore") Double engagementScore,
        @Param("riskScore") Double riskScore,
        @Param("isAnomaly") Boolean isAnomaly,
        @Param("anomalyReasons") String anomalyReasons);
    
    /**
     * Find navigation patterns
     */
    @Query("SELECT ba.entryPoint, ba.exitPoint, ba.completedGoal, COUNT(ba) " +
           "FROM BehaviorAnalytics ba " +
           "WHERE ba.userId = :userId " +
           "AND ba.activityDate >= :sinceDate " +
           "GROUP BY ba.entryPoint, ba.exitPoint, ba.completedGoal")
    List<Object[]> findNavigationPatterns(
        @Param("userId") UUID userId,
        @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Get transaction behavior analysis
     */
    @Query("SELECT ba FROM BehaviorAnalytics ba " +
           "WHERE ba.userId = :userId " +
           "AND ba.activityType = 'TRANSACTION' " +
           "AND ba.transactionAmount IS NOT NULL " +
           "ORDER BY ba.activityDate DESC")
    Page<BehaviorAnalytics> getTransactionBehaviors(
        @Param("userId") UUID userId,
        Pageable pageable);
    
    /**
     * Delete old behavior analytics
     */
    void deleteByActivityDateBefore(LocalDateTime cutoffDate);
}