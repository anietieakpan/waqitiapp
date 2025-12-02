package com.waqiti.analytics.repository;

import com.waqiti.analytics.model.UserSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for UserSession entities
 */
@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    
    /**
     * Find session by session ID
     */
    Optional<UserSession> findBySessionId(String sessionId);
    
    /**
     * Find sessions by user ID
     */
    List<UserSession> findByUserId(String userId);
    
    /**
     * Find sessions by user ID with pagination
     */
    Page<UserSession> findByUserIdOrderByStartTimeDesc(String userId, Pageable pageable);
    
    /**
     * Find sessions by user ID and date range
     */
    List<UserSession> findByUserIdAndStartTimeBetween(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find sessions by user ID and date range (Instant version)
     */
    List<UserSession> findByUserIdAndStartTimeBetween(String userId, Instant startDate, Instant endDate);
    
    /**
     * Find active sessions
     */
    List<UserSession> findByActiveTrue();
    
    /**
     * Find active sessions for user
     */
    List<UserSession> findByUserIdAndActiveTrue(String userId);
    
    /**
     * Find sessions by platform
     */
    List<UserSession> findByPlatform(String platform);
    
    /**
     * Find sessions by platform and date range
     */
    List<UserSession> findByPlatformAndStartTimeBetween(String platform, Instant startDate, Instant endDate);
    
    /**
     * Count sessions by user ID
     */
    long countByUserId(String userId);
    
    /**
     * Count active sessions by user ID
     */
    long countByUserIdAndActiveTrue(String userId);
    
    /**
     * Find sessions with minimum duration
     */
    @Query("SELECT us FROM UserSession us WHERE us.duration >= :minDuration")
    List<UserSession> findSessionsWithMinimumDuration(@Param("minDuration") Duration minDuration);
    
    /**
     * Find sessions by user ID and minimum duration
     */
    @Query("SELECT us FROM UserSession us WHERE us.userId = :userId AND us.duration >= :minDuration")
    List<UserSession> findByUserIdAndMinimumDuration(@Param("userId") String userId, 
                                                     @Param("minDuration") Duration minDuration);
    
    /**
     * Get average session duration for user
     */
    @Query("SELECT AVG(us.duration) FROM UserSession us WHERE us.userId = :userId")
    Duration getAverageSessionDurationByUserId(@Param("userId") String userId);
    
    /**
     * Get session count by platform
     */
    @Query("SELECT us.platform, COUNT(us) FROM UserSession us GROUP BY us.platform")
    List<Object[]> getSessionCountByPlatform();
    
    /**
     * Get daily session counts for user
     */
    @Query("SELECT DATE(us.startTime), COUNT(us) FROM UserSession us " +
           "WHERE us.userId = :userId AND us.startTime BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(us.startTime) ORDER BY DATE(us.startTime)")
    List<Object[]> getDailySessionCounts(@Param("userId") String userId,
                                        @Param("startDate") Instant startDate,
                                        @Param("endDate") Instant endDate);
    
    /**
     * Get hourly session distribution
     */
    @Query("SELECT HOUR(us.startTime), COUNT(us) FROM UserSession us " +
           "WHERE us.userId = :userId " +
           "GROUP BY HOUR(us.startTime) ORDER BY HOUR(us.startTime)")
    List<Object[]> getHourlySessionDistribution(@Param("userId") String userId);
    
    /**
     * Find long sessions (above threshold)
     */
    @Query("SELECT us FROM UserSession us WHERE us.duration > :threshold")
    List<UserSession> findLongSessions(@Param("threshold") Duration threshold);
    
    /**
     * Find short sessions (below threshold)
     */
    @Query("SELECT us FROM UserSession us WHERE us.duration < :threshold AND us.duration IS NOT NULL")
    List<UserSession> findShortSessions(@Param("threshold") Duration threshold);
    
    /**
     * Get sessions with high event count
     */
    @Query("SELECT us FROM UserSession us WHERE us.events > :minEvents")
    List<UserSession> findHighActivitySessions(@Param("minEvents") int minEvents);
    
    /**
     * Get sessions with high screen views
     */
    @Query("SELECT us FROM UserSession us WHERE us.screenViews > :minScreenViews")
    List<UserSession> findHighScreenViewSessions(@Param("minScreenViews") int minScreenViews);
    
    /**
     * Find recent sessions for user
     */
    @Query("SELECT us FROM UserSession us WHERE us.userId = :userId " +
           "ORDER BY us.startTime DESC")
    List<UserSession> findRecentSessionsByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * Find sessions by device info (if available)
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT us FROM UserSession us WHERE us.deviceInfo LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:deviceInfo, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')")
    List<UserSession> findByDeviceInfo(@Param("deviceInfo") String deviceInfo);
    
    /**
     * Get session statistics for date range
     */
    @Query("SELECT COUNT(us), AVG(us.duration), SUM(us.events), SUM(us.screenViews) " +
           "FROM UserSession us WHERE us.startTime BETWEEN :startDate AND :endDate")
    Object[] getSessionStatistics(@Param("startDate") Instant startDate,
                                 @Param("endDate") Instant endDate);
    
    /**
     * Find sessions that ended recently
     */
    @Query("SELECT us FROM UserSession us WHERE us.endTime IS NOT NULL " +
           "AND us.endTime > :cutoffTime ORDER BY us.endTime DESC")
    List<UserSession> findRecentlyEndedSessions(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Find sessions with specific activity pattern
     */
    @Query("SELECT us FROM UserSession us WHERE us.lastActivity > :activityTime")
    List<UserSession> findSessionsWithRecentActivity(@Param("activityTime") Instant activityTime);
    
    /**
     * Delete old inactive sessions
     */
    void deleteByActiveFalseAndEndTimeBefore(Instant cutoffTime);
    
    /**
     * Find abandoned sessions (no recent activity)
     */
    @Query("SELECT us FROM UserSession us WHERE us.active = true " +
           "AND us.lastActivity < :abandonmentTime")
    List<UserSession> findAbandonedSessions(@Param("abandonmentTime") Instant abandonmentTime);
    
    /**
     * Get concurrent sessions count for user
     */
    @Query("SELECT COUNT(us) FROM UserSession us WHERE us.userId = :userId " +
           "AND us.active = true AND us.startTime <= :timestamp " +
           "AND (us.endTime IS NULL OR us.endTime >= :timestamp)")
    long getConcurrentSessionsCount(@Param("userId") String userId, @Param("timestamp") Instant timestamp);
}