package com.waqiti.security.repository;

import com.waqiti.security.model.FailedAuthEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Failed Authentication Events
 */
@Repository
public interface FailedEventRepository extends JpaRepository<FailedAuthEvent, String> {

    /**
     * Find failed events by user ID
     */
    List<FailedAuthEvent> findByUserId(String userId);

    /**
     * Find recent failed events by user
     */
    @Query("SELECT f FROM FailedAuthEvent f WHERE f.userId = :userId AND f.failedAt >= :since ORDER BY f.failedAt DESC")
    List<FailedAuthEvent> findRecentByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Find failed events by IP address
     */
    List<FailedAuthEvent> findByIpAddress(String ipAddress);

    /**
     * Find recent failed events by IP
     */
    @Query("SELECT f FROM FailedAuthEvent f WHERE f.ipAddress = :ipAddress AND f.failedAt >= :since ORDER BY f.failedAt DESC")
    List<FailedAuthEvent> findRecentByIpAddress(@Param("ipAddress") String ipAddress, @Param("since") Instant since);

    /**
     * Find failed events by device ID
     */
    List<FailedAuthEvent> findByDeviceId(String deviceId);

    /**
     * Find recent failed events by device
     */
    @Query("SELECT f FROM FailedAuthEvent f WHERE f.deviceId = :deviceId AND f.failedAt >= :since ORDER BY f.failedAt DESC")
    List<FailedAuthEvent> findRecentByDeviceId(@Param("deviceId") String deviceId, @Param("since") Instant since);

    /**
     * Find failed events by failure reason
     */
    List<FailedAuthEvent> findByFailureReason(String failureReason);

    /**
     * Count failed attempts by user
     */
    long countByUserId(String userId);

    /**
     * Count recent failed attempts by user
     */
    @Query("SELECT COUNT(f) FROM FailedAuthEvent f WHERE f.userId = :userId AND f.failedAt >= :since")
    long countRecentByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Count failed attempts by IP address
     */
    long countByIpAddress(String ipAddress);

    /**
     * Count recent failed attempts by IP
     */
    @Query("SELECT COUNT(f) FROM FailedAuthEvent f WHERE f.ipAddress = :ipAddress AND f.failedAt >= :since")
    long countRecentByIpAddress(@Param("ipAddress") String ipAddress, @Param("since") Instant since);

    /**
     * Count failed attempts by device
     */
    long countByDeviceId(String deviceId);

    /**
     * Count recent failed attempts by device
     */
    @Query("SELECT COUNT(f) FROM FailedAuthEvent f WHERE f.deviceId = :deviceId AND f.failedAt >= :since")
    long countRecentByDeviceId(@Param("deviceId") String deviceId, @Param("since") Instant since);

    /**
     * Find failed events in date range
     */
    @Query("SELECT f FROM FailedAuthEvent f WHERE f.failedAt BETWEEN :startDate AND :endDate ORDER BY f.failedAt DESC")
    List<FailedAuthEvent> findByDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    /**
     * Find failed events by user and date range
     */
    @Query("SELECT f FROM FailedAuthEvent f WHERE f.userId = :userId AND f.failedAt BETWEEN :startDate AND :endDate ORDER BY f.failedAt DESC")
    List<FailedAuthEvent> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    /**
     * Find failed events with pagination
     */
    Page<FailedAuthEvent> findByUserId(String userId, Pageable pageable);

    /**
     * Find brute force patterns - multiple failures from same IP
     */
    @Query("SELECT f.ipAddress, COUNT(f) FROM FailedAuthEvent f WHERE f.failedAt >= :since GROUP BY f.ipAddress HAVING COUNT(f) >= :threshold")
    List<Object[]> findBruteForcePatterns(@Param("since") Instant since, @Param("threshold") long threshold);

    /**
     * Find credential stuffing patterns - multiple user attempts from same IP
     */
    @Query("SELECT f.ipAddress, COUNT(DISTINCT f.userId) FROM FailedAuthEvent f WHERE f.failedAt >= :since GROUP BY f.ipAddress HAVING COUNT(DISTINCT f.userId) >= :threshold")
    List<Object[]> findCredentialStuffingPatterns(@Param("since") Instant since, @Param("threshold") long threshold);

    /**
     * Find password spray patterns - same password attempt across users
     */
    @Query("SELECT f.userId, f.ipAddress, COUNT(f) FROM FailedAuthEvent f WHERE f.failedAt >= :since AND f.failureReason = 'INVALID_PASSWORD' GROUP BY f.userId, f.ipAddress")
    List<Object[]> findPasswordSprayPatterns(@Param("since") Instant since);

    /**
     * Get failure statistics by reason
     */
    @Query("SELECT f.failureReason, COUNT(f) FROM FailedAuthEvent f WHERE f.failedAt >= :since GROUP BY f.failureReason")
    List<Object[]> getFailureStatsByReason(@Param("since") Instant since);

    /**
     * Find top attacking IPs
     */
    @Query("SELECT f.ipAddress, COUNT(f) as cnt FROM FailedAuthEvent f WHERE f.failedAt >= :since GROUP BY f.ipAddress ORDER BY cnt DESC")
    List<Object[]> findTopAttackingIPs(@Param("since") Instant since, Pageable pageable);

    /**
     * Find suspicious login attempts with multiple criteria
     */
    @Query("SELECT f FROM FailedAuthEvent f WHERE " +
           "(:userId IS NULL OR f.userId = :userId) AND " +
           "(:ipAddress IS NULL OR f.ipAddress = :ipAddress) AND " +
           "(:deviceId IS NULL OR f.deviceId = :deviceId) AND " +
           "(:failureReason IS NULL OR f.failureReason = :failureReason) AND " +
           "(:startDate IS NULL OR f.failedAt >= :startDate) AND " +
           "(:endDate IS NULL OR f.failedAt <= :endDate)")
    Page<FailedAuthEvent> findByCriteria(
        @Param("userId") String userId,
        @Param("ipAddress") String ipAddress,
        @Param("deviceId") String deviceId,
        @Param("failureReason") String failureReason,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        Pageable pageable
    );

    /**
     * Delete old failed events (for cleanup)
     */
    @Query("DELETE FROM FailedAuthEvent f WHERE f.failedAt < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") Instant cutoffDate);
}