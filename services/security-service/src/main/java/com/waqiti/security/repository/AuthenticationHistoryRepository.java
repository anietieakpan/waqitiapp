package com.waqiti.security.repository;

import com.waqiti.security.model.AuthenticationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Authentication History
 */
@Repository
public interface AuthenticationHistoryRepository extends JpaRepository<AuthenticationHistory, String> {

    /**
     * Find authentication history by user ID
     */
    List<AuthenticationHistory> findByUserId(String userId);

    /**
     * Find recent authentication history by user
     */
    @Query("SELECT h FROM AuthenticationHistory h WHERE h.userId = :userId AND h.authenticatedAt >= :since ORDER BY h.authenticatedAt DESC")
    List<AuthenticationHistory> findRecentByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Find successful authentications by user
     */
    @Query("SELECT h FROM AuthenticationHistory h WHERE h.userId = :userId AND h.successful = true ORDER BY h.authenticatedAt DESC")
    List<AuthenticationHistory> findSuccessfulByUserId(@Param("userId") String userId);

    /**
     * Find failed authentications by user
     */
    @Query("SELECT h FROM AuthenticationHistory h WHERE h.userId = :userId AND h.successful = false ORDER BY h.authenticatedAt DESC")
    List<AuthenticationHistory> findFailedByUserId(@Param("userId") String userId);

    /**
     * Find history by IP address
     */
    List<AuthenticationHistory> findByIpAddress(String ipAddress);

    /**
     * Find recent history by IP
     */
    @Query("SELECT h FROM AuthenticationHistory h WHERE h.ipAddress = :ipAddress AND h.authenticatedAt >= :since ORDER BY h.authenticatedAt DESC")
    List<AuthenticationHistory> findRecentByIpAddress(@Param("ipAddress") String ipAddress, @Param("since") Instant since);

    /**
     * Find history by device ID
     */
    List<AuthenticationHistory> findByDeviceId(String deviceId);

    /**
     * Find recent history by device
     */
    @Query("SELECT h FROM AuthenticationHistory h WHERE h.deviceId = :deviceId AND h.authenticatedAt >= :since ORDER BY h.authenticatedAt DESC")
    List<AuthenticationHistory> findRecentByDeviceId(@Param("deviceId") String deviceId, @Param("since") Instant since);

    /**
     * Count total authentications by user
     */
    long countByUserId(String userId);

    /**
     * Count recent authentications by user
     */
    @Query("SELECT COUNT(h) FROM AuthenticationHistory h WHERE h.userId = :userId AND h.authenticatedAt >= :since")
    long countRecentByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Count successful authentications by user
     */
    @Query("SELECT COUNT(h) FROM AuthenticationHistory h WHERE h.userId = :userId AND h.successful = true AND h.authenticatedAt >= :since")
    long countSuccessfulByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Count failed authentications by user
     */
    @Query("SELECT COUNT(h) FROM AuthenticationHistory h WHERE h.userId = :userId AND h.successful = false AND h.authenticatedAt >= :since")
    long countFailedByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Find history in date range
     */
    @Query("SELECT h FROM AuthenticationHistory h WHERE h.authenticatedAt BETWEEN :startDate AND :endDate ORDER BY h.authenticatedAt DESC")
    List<AuthenticationHistory> findByDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    /**
     * Find history by user and date range
     */
    @Query("SELECT h FROM AuthenticationHistory h WHERE h.userId = :userId AND h.authenticatedAt BETWEEN :startDate AND :endDate ORDER BY h.authenticatedAt DESC")
    List<AuthenticationHistory> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    /**
     * Find history with pagination
     */
    Page<AuthenticationHistory> findByUserId(String userId, Pageable pageable);

    /**
     * Get unique locations for user
     */
    @Query("SELECT DISTINCT h.country, h.city FROM AuthenticationHistory h WHERE h.userId = :userId AND h.successful = true AND h.authenticatedAt >= :since")
    List<Object[]> findUniqueLocationsByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Get unique devices for user
     */
    @Query("SELECT DISTINCT h.deviceId FROM AuthenticationHistory h WHERE h.userId = :userId AND h.successful = true AND h.authenticatedAt >= :since")
    List<String> findUniqueDevicesByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Get unique IPs for user
     */
    @Query("SELECT DISTINCT h.ipAddress FROM AuthenticationHistory h WHERE h.userId = :userId AND h.successful = true AND h.authenticatedAt >= :since")
    List<String> findUniqueIPsByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Get authentication patterns by time of day
     */
    @Query("SELECT HOUR(h.authenticatedAt), COUNT(h) FROM AuthenticationHistory h WHERE h.userId = :userId AND h.authenticatedAt >= :since GROUP BY HOUR(h.authenticatedAt)")
    List<Object[]> getAuthPatternsByTimeOfDay(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Find concurrent logins (same user, different IPs, within time window)
     */
    @Query("SELECT h1 FROM AuthenticationHistory h1, AuthenticationHistory h2 WHERE " +
           "h1.userId = h2.userId AND h1.userId = :userId AND " +
           "h1.ipAddress != h2.ipAddress AND " +
           "h1.successful = true AND h2.successful = true AND " +
           "ABS(TIMESTAMPDIFF(SECOND, h1.authenticatedAt, h2.authenticatedAt)) <= :timeWindowSeconds AND " +
           "h1.historyId < h2.historyId")
    List<AuthenticationHistory> findConcurrentLogins(@Param("userId") String userId, @Param("timeWindowSeconds") long timeWindowSeconds);

    /**
     * Delete old history records (for cleanup)
     */
    @Query("DELETE FROM AuthenticationHistory h WHERE h.authenticatedAt < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") Instant cutoffDate);
}
