package com.waqiti.analytics.repository;

import com.waqiti.analytics.model.UserMetrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for UserMetrics entities
 */
@Repository
public interface UserMetricsRepository extends JpaRepository<UserMetrics, Long> {
    
    /**
     * Find user metrics by user ID
     */
    Optional<UserMetrics> findByUserId(String userId);
    
    /**
     * Find user metrics by user ID and date range
     */
    List<UserMetrics> findByUserIdAndCreatedAtBetween(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find all metrics for a list of user IDs
     */
    List<UserMetrics> findByUserIdIn(List<String> userIds);
    
    /**
     * Find metrics with pagination
     */
    Page<UserMetrics> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * Find top users by transaction count
     */
    @Query("SELECT um FROM UserMetrics um ORDER BY um.transactionCount DESC")
    List<UserMetrics> findTopUsersByTransactionCount(Pageable pageable);
    
    /**
     * Find top users by transaction volume
     */
    @Query("SELECT um FROM UserMetrics um ORDER BY um.transactionVolume DESC")
    List<UserMetrics> findTopUsersByTransactionVolume(Pageable pageable);
    
    /**
     * Find users with high engagement score
     */
    @Query("SELECT um FROM UserMetrics um WHERE um.engagementScore > :minScore")
    List<UserMetrics> findUsersWithHighEngagement(@Param("minScore") double minScore);
    
    /**
     * Get metrics summary for date range
     */
    @Query("SELECT COUNT(um), SUM(um.transactionCount), SUM(um.transactionVolume) " +
           "FROM UserMetrics um WHERE um.createdAt BETWEEN :startDate AND :endDate")
    Object[] getMetricsSummary(@Param("startDate") LocalDateTime startDate, 
                              @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find users at risk of churning
     */
    @Query("SELECT um FROM UserMetrics um WHERE um.churnRisk > :riskThreshold")
    List<UserMetrics> findUsersAtRiskOfChurning(@Param("riskThreshold") double riskThreshold);
    
    /**
     * Delete old metrics older than specified date
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
    
    /**
     * Check if user metrics exist for specific user and date
     */
    boolean existsByUserIdAndCreatedAtBetween(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find average metrics by time period
     */
    @Query("SELECT AVG(um.transactionCount), AVG(um.transactionVolume), AVG(um.engagementScore) " +
           "FROM UserMetrics um WHERE um.createdAt BETWEEN :startDate AND :endDate")
    Object[] getAverageMetrics(@Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find users by segment
     */
    @Query("SELECT um FROM UserMetrics um WHERE :segment MEMBER OF um.segments")
    List<UserMetrics> findUsersBySegment(@Param("segment") String segment);
    
    /**
     * Get user count by engagement level
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN um.engagementScore >= 80 THEN 1 END) as high, " +
           "COUNT(CASE WHEN um.engagementScore >= 50 AND um.engagementScore < 80 THEN 1 END) as medium, " +
           "COUNT(CASE WHEN um.engagementScore < 50 THEN 1 END) as low " +
           "FROM UserMetrics um")
    Object[] getEngagementDistribution();
}