package com.waqiti.analytics.repository;

import com.waqiti.analytics.model.UserEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Repository interface for UserEvent entities
 */
@Repository
public interface UserEventRepository extends JpaRepository<UserEvent, Long> {
    
    /**
     * Find events by user ID
     */
    List<UserEvent> findByUserId(String userId);
    
    /**
     * Find events by user ID and timestamp range
     */
    List<UserEvent> findByUserIdAndTimestampBetween(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find events by user ID and timestamp range (Instant version)
     */
    List<UserEvent> findByUserIdAndTimestampBetween(String userId, Instant startDate, Instant endDate);
    
    /**
     * Find events by event name
     */
    List<UserEvent> findByEventName(String eventName);
    
    /**
     * Find events by event name and date range
     */
    List<UserEvent> findByEventNameAndTimestampBetween(String eventName, Instant startDate, Instant endDate);
    
    /**
     * Find events by session ID
     */
    List<UserEvent> findBySessionId(String sessionId);
    
    /**
     * Find events by multiple user IDs
     */
    List<UserEvent> findByUserIdIn(List<String> userIds);
    
    /**
     * Find events with pagination
     */
    Page<UserEvent> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
    
    /**
     * Find events by platform
     */
    List<UserEvent> findByPlatform(String platform);
    
    /**
     * Get event count by user ID
     */
    long countByUserId(String userId);
    
    /**
     * Get event count by event name
     */
    long countByEventName(String eventName);
    
    /**
     * Get event count by user ID and date range
     */
    long countByUserIdAndTimestampBetween(String userId, Instant startDate, Instant endDate);
    
    /**
     * Find events by category
     */
    List<UserEvent> findByEventCategory(String category);
    
    /**
     * Find recent events for user
     */
    @Query("SELECT ue FROM UserEvent ue WHERE ue.userId = :userId ORDER BY ue.timestamp DESC")
    List<UserEvent> findRecentEventsByUserId(@Param("userId") String userId, Pageable pageable);
    
    /**
     * Get event counts grouped by event name
     */
    @Query("SELECT ue.eventName, COUNT(ue) FROM UserEvent ue " +
           "WHERE ue.userId = :userId AND ue.timestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY ue.eventName")
    List<Object[]> getEventCountsByName(@Param("userId") String userId, 
                                       @Param("startDate") Instant startDate,
                                       @Param("endDate") Instant endDate);
    
    /**
     * Get daily event counts
     */
    @Query("SELECT DATE(ue.timestamp), COUNT(ue) FROM UserEvent ue " +
           "WHERE ue.userId = :userId AND ue.timestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(ue.timestamp) ORDER BY DATE(ue.timestamp)")
    List<Object[]> getDailyEventCounts(@Param("userId") String userId,
                                      @Param("startDate") Instant startDate,
                                      @Param("endDate") Instant endDate);
    
    /**
     * Get hourly event distribution
     */
    @Query("SELECT HOUR(ue.timestamp), COUNT(ue) FROM UserEvent ue " +
           "WHERE ue.userId = :userId " +
           "GROUP BY HOUR(ue.timestamp) ORDER BY HOUR(ue.timestamp)")
    List<Object[]> getHourlyEventDistribution(@Param("userId") String userId);
    
    /**
     * Find users who performed specific event
     */
    @Query("SELECT DISTINCT ue.userId FROM UserEvent ue WHERE ue.eventName = :eventName " +
           "AND ue.timestamp BETWEEN :startDate AND :endDate")
    List<String> findUsersWhoPerformedEvent(@Param("eventName") String eventName,
                                          @Param("startDate") Instant startDate,
                                          @Param("endDate") Instant endDate);
    
    /**
     * Find events with specific property value
     */
    @Query("SELECT ue FROM UserEvent ue WHERE JSON_EXTRACT(ue.eventProperties, :propertyPath) = :propertyValue")
    List<UserEvent> findByEventProperty(@Param("propertyPath") String propertyPath, 
                                       @Param("propertyValue") String propertyValue);
    
    /**
     * Delete old events before specified date
     */
    void deleteByTimestampBefore(Instant cutoffDate);
    
    /**
     * Get most frequent events
     */
    @Query("SELECT ue.eventName, COUNT(ue) as eventCount FROM UserEvent ue " +
           "GROUP BY ue.eventName ORDER BY eventCount DESC")
    List<Object[]> getMostFrequentEvents(Pageable pageable);
    
    /**
     * Get platform distribution
     */
    @Query("SELECT ue.platform, COUNT(ue) FROM UserEvent ue " +
           "WHERE ue.timestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY ue.platform")
    List<Object[]> getPlatformDistribution(@Param("startDate") Instant startDate,
                                          @Param("endDate") Instant endDate);
    
    /**
     * Find events by location (if available)
     */
    @Query("SELECT ue FROM UserEvent ue WHERE ue.location IS NOT NULL " +
           "AND ue.timestamp BETWEEN :startDate AND :endDate")
    List<UserEvent> findEventsWithLocation(@Param("startDate") Instant startDate,
                                          @Param("endDate") Instant endDate);
    
    /**
     * Get session event counts
     */
    @Query("SELECT ue.sessionId, COUNT(ue) FROM UserEvent ue " +
           "WHERE ue.userId = :userId GROUP BY ue.sessionId")
    List<Object[]> getSessionEventCounts(@Param("userId") String userId);
}