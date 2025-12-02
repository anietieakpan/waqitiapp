package com.waqiti.audit.repository;

import com.waqiti.audit.domain.UserActivity;
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
 * Repository for user activity tracking and analysis
 */
@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, UUID> {
    
    /**
     * Find activities by user ID
     */
    Page<UserActivity> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
    
    /**
     * Find activities by user and time range
     */
    List<UserActivity> findByUserIdAndTimestampBetween(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find last activity for a user
     */
    Optional<UserActivity> findTopByUserIdOrderByTimestampDesc(String userId);
    
    /**
     * Find activities by session
     */
    List<UserActivity> findBySessionIdOrderByTimestamp(String sessionId);
    
    /**
     * Find anomalous activities
     */
    @Query("SELECT u FROM UserActivity u WHERE u.anomalyDetected = true " +
           "AND u.timestamp >= :startDate ORDER BY u.riskScore DESC")
    List<UserActivity> findAnomalousActivities(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Find high-risk activities
     */
    @Query("SELECT u FROM UserActivity u WHERE u.riskScore > :threshold " +
           "AND u.timestamp >= :startDate")
    List<UserActivity> findHighRiskActivities(@Param("threshold") Double threshold,
                                               @Param("startDate") LocalDateTime startDate);
    
    /**
     * Count activities by type for a user
     */
    @Query("SELECT u.activityType, COUNT(u) FROM UserActivity u " +
           "WHERE u.userId = :userId AND u.timestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY u.activityType")
    List<Object[]> countActivitiesByType(@Param("userId") String userId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find unusual access patterns
     */
    @Query("SELECT u FROM UserActivity u WHERE u.userId = :userId " +
           "AND u.timestamp >= :startDate " +
           "AND (u.ipAddress NOT IN (SELECT DISTINCT u2.ipAddress FROM UserActivity u2 " +
           "WHERE u2.userId = :userId AND u2.timestamp < :startDate))")
    List<UserActivity> findUnusualAccessPatterns(@Param("userId") String userId,
                                                  @Param("startDate") LocalDateTime startDate);
    
    /**
     * Calculate average response time
     */
    @Query("SELECT AVG(u.responseTimeMs) FROM UserActivity u " +
           "WHERE u.userId = :userId AND u.timestamp BETWEEN :startDate AND :endDate")
    Double calculateAverageResponseTime(@Param("userId") String userId,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find failed activities
     */
    List<UserActivity> findByUserIdAndSuccessFalseAndTimestampBetween(String userId, 
                                                                        LocalDateTime startDate, 
                                                                        LocalDateTime endDate);
    
    /**
     * Find activities by IP address
     */
    List<UserActivity> findByIpAddressAndTimestampBetween(String ipAddress, 
                                                           LocalDateTime startDate, 
                                                           LocalDateTime endDate);
    
    /**
     * Find concurrent sessions
     */
    @Query("SELECT DISTINCT u.sessionId FROM UserActivity u " +
           "WHERE u.userId = :userId AND u.timestamp BETWEEN :startDate AND :endDate")
    List<String> findConcurrentSessions(@Param("userId") String userId,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);
}