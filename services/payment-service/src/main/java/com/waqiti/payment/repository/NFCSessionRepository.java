package com.waqiti.payment.repository;

import com.waqiti.payment.entity.NFCSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for NFC sessions
 */
@Repository
public interface NFCSessionRepository extends JpaRepository<NFCSession, Long> {

    /**
     * Find session by session ID
     */
    Optional<NFCSession> findBySessionId(String sessionId);

    /**
     * Find session by session token
     */
    Optional<NFCSession> findBySessionToken(String sessionToken);

    /**
     * Find active sessions by user ID
     */
    @Query("SELECT s FROM NFCSession s WHERE " +
           "(s.userId = :userId OR s.merchantId = :userId) AND " +
           "s.status = 'ACTIVE' AND s.expiresAt > :now " +
           "ORDER BY s.createdAt DESC")
    List<NFCSession> findActiveSessionsByUserId(@Param("userId") String userId, @Param("now") Instant now);

    /**
     * Find active sessions by device ID
     */
    @Query("SELECT s FROM NFCSession s WHERE " +
           "s.deviceId = :deviceId AND " +
           "s.status = 'ACTIVE' AND s.expiresAt > :now " +
           "ORDER BY s.createdAt DESC")
    List<NFCSession> findActiveSessionsByDeviceId(@Param("deviceId") String deviceId, @Param("now") Instant now);

    /**
     * Find sessions by session type
     */
    Page<NFCSession> findBySessionTypeOrderByCreatedAtDesc(String sessionType, Pageable pageable);

    /**
     * Find sessions by status
     */
    Page<NFCSession> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * Find expired sessions that are still marked as active
     */
    @Query("SELECT s FROM NFCSession s WHERE " +
           "s.status = 'ACTIVE' AND s.expiresAt <= :now")
    List<NFCSession> findExpiredActiveSessions(@Param("now") Instant now);

    /**
     * Find sessions expiring soon
     */
    @Query("SELECT s FROM NFCSession s WHERE " +
           "s.status = 'ACTIVE' AND " +
           "s.expiresAt > :now AND s.expiresAt <= :soonExpiry " +
           "ORDER BY s.expiresAt ASC")
    List<NFCSession> findSessionsExpiringSoon(
            @Param("now") Instant now,
            @Param("soonExpiry") Instant soonExpiry);

    /**
     * Find sessions by merchant ID
     */
    Page<NFCSession> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);

    /**
     * Find sessions by user ID
     */
    Page<NFCSession> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Find sessions within date range
     */
    @Query("SELECT s FROM NFCSession s WHERE " +
           "s.createdAt >= :startDate AND s.createdAt <= :endDate " +
           "ORDER BY s.createdAt DESC")
    Page<NFCSession> findByDateRangeOrderByCreatedAtDesc(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    /**
     * Count active sessions for a user
     */
    @Query("SELECT COUNT(s) FROM NFCSession s WHERE " +
           "(s.userId = :userId OR s.merchantId = :userId) AND " +
           "s.status = 'ACTIVE' AND s.expiresAt > :now")
    Long countActiveSessionsForUser(@Param("userId") String userId, @Param("now") Instant now);

    /**
     * Count active sessions for a device
     */
    @Query("SELECT COUNT(s) FROM NFCSession s WHERE " +
           "s.deviceId = :deviceId AND " +
           "s.status = 'ACTIVE' AND s.expiresAt > :now")
    Long countActiveSessionsForDevice(@Param("deviceId") String deviceId, @Param("now") Instant now);

    /**
     * Find sessions by location proximity
     */
    @Query("SELECT s FROM NFCSession s WHERE " +
           "s.latitude IS NOT NULL AND s.longitude IS NOT NULL AND " +
           "6371 * acos(cos(radians(:lat)) * cos(radians(s.latitude)) * " +
           "cos(radians(s.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(s.latitude))) <= :radiusKm " +
           "ORDER BY s.createdAt DESC")
    Page<NFCSession> findByLocationProximity(
            @Param("lat") Double latitude,
            @Param("lng") Double longitude,
            @Param("radiusKm") Double radiusKm,
            Pageable pageable);

    /**
     * Get session statistics by type
     */
    @Query("SELECT " +
           "s.sessionType, " +
           "COUNT(s) as totalSessions, " +
           "SUM(CASE WHEN s.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedSessions, " +
           "SUM(CASE WHEN s.status = 'EXPIRED' THEN 1 ELSE 0 END) as expiredSessions, " +
           "AVG(s.transactionCount) as avgTransactionsPerSession " +
           "FROM NFCSession s WHERE " +
           "s.createdAt >= :startDate AND s.createdAt <= :endDate " +
           "GROUP BY s.sessionType")
    List<Object[]> getSessionStatsByType(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    /**
     * Get most active devices by session count
     */
    @Query("SELECT " +
           "s.deviceId, " +
           "COUNT(s) as sessionCount, " +
           "SUM(s.transactionCount) as totalTransactions " +
           "FROM NFCSession s WHERE " +
           "s.createdAt >= :startDate AND s.createdAt <= :endDate " +
           "GROUP BY s.deviceId " +
           "ORDER BY sessionCount DESC")
    List<Object[]> getMostActiveDevices(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    /**
     * Find sessions with high transaction volume
     */
    @Query("SELECT s FROM NFCSession s WHERE " +
           "s.totalAmountProcessed > :minAmount " +
           "ORDER BY s.totalAmountProcessed DESC")
    List<NFCSession> findHighVolumeSession(@Param("minAmount") java.math.BigDecimal minAmount);

    /**
     * Update session status
     */
    @Modifying
    @Query("UPDATE NFCSession s SET " +
           "s.status = :status, " +
           "s.updatedAt = :updatedAt, " +
           "s.completedAt = CASE WHEN :status IN ('COMPLETED', 'EXPIRED', 'CANCELLED') THEN :updatedAt ELSE s.completedAt END " +
           "WHERE s.sessionId = :sessionId")
    int updateSessionStatus(
            @Param("sessionId") String sessionId,
            @Param("status") String status,
            @Param("updatedAt") Instant updatedAt);

    /**
     * Update session activity
     */
    @Modifying
    @Query("UPDATE NFCSession s SET " +
           "s.lastActivityAt = :activityTime, " +
           "s.updatedAt = :activityTime " +
           "WHERE s.sessionId = :sessionId")
    int updateSessionActivity(
            @Param("sessionId") String sessionId,
            @Param("activityTime") Instant activityTime);

    /**
     * Increment transaction count for session
     */
    @Modifying
    @Query("UPDATE NFCSession s SET " +
           "s.transactionCount = COALESCE(s.transactionCount, 0) + 1, " +
           "s.remainingTransactions = CASE WHEN s.remainingTransactions IS NOT NULL AND s.remainingTransactions > 0 " +
           "                               THEN s.remainingTransactions - 1 " +
           "                               ELSE s.remainingTransactions END, " +
           "s.totalAmountProcessed = COALESCE(s.totalAmountProcessed, 0) + :amount, " +
           "s.lastTransactionId = :transactionId, " +
           "s.lastActivityAt = :activityTime, " +
           "s.updatedAt = :activityTime " +
           "WHERE s.sessionId = :sessionId")
    int incrementSessionTransactionCount(
            @Param("sessionId") String sessionId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("transactionId") String transactionId,
            @Param("activityTime") Instant activityTime);

    /**
     * Expire all active sessions for a device
     */
    @Modifying
    @Query("UPDATE NFCSession s SET " +
           "s.status = 'EXPIRED', " +
           "s.completedAt = :expiredAt, " +
           "s.updatedAt = :expiredAt " +
           "WHERE s.deviceId = :deviceId AND s.status = 'ACTIVE'")
    int expireAllSessionsForDevice(
            @Param("deviceId") String deviceId,
            @Param("expiredAt") Instant expiredAt);

    /**
     * Expire all active sessions for a user
     */
    @Modifying
    @Query("UPDATE NFCSession s SET " +
           "s.status = 'EXPIRED', " +
           "s.completedAt = :expiredAt, " +
           "s.updatedAt = :expiredAt " +
           "WHERE (s.userId = :userId OR s.merchantId = :userId) AND s.status = 'ACTIVE'")
    int expireAllSessionsForUser(
            @Param("userId") String userId,
            @Param("expiredAt") Instant expiredAt);

    /**
     * Auto-expire sessions based on expiration time
     */
    @Modifying
    @Query("UPDATE NFCSession s SET " +
           "s.status = 'EXPIRED', " +
           "s.completedAt = :now, " +
           "s.updatedAt = :now " +
           "WHERE s.status = 'ACTIVE' AND s.expiresAt <= :now")
    int autoExpireSessions(@Param("now") Instant now);

    /**
     * Clean up old completed sessions
     */
    @Modifying
    @Query("DELETE FROM NFCSession s WHERE " +
           "s.status IN ('COMPLETED', 'EXPIRED', 'CANCELLED') AND " +
           "s.completedAt < :cutoffDate")
    int cleanupOldSessions(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Find sessions requiring cleanup
     */
    @Query("SELECT s.sessionId FROM NFCSession s WHERE " +
           "s.status IN ('COMPLETED', 'EXPIRED', 'CANCELLED') AND " +
           "s.completedAt < :cutoffDate")
    List<String> findSessionsForCleanup(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Get average session duration by type
     */
    @Query("SELECT " +
           "s.sessionType, " +
           "AVG(TIMESTAMPDIFF(SECOND, s.createdAt, COALESCE(s.completedAt, s.updatedAt))) as avgDurationSeconds " +
           "FROM NFCSession s WHERE " +
           "s.createdAt >= :startDate AND s.createdAt <= :endDate " +
           "GROUP BY s.sessionType")
    List<Object[]> getAverageSessionDurationByType(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);
}