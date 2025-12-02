package com.waqiti.auth.repository;

import com.waqiti.auth.domain.UserSession;
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
 * Enterprise-grade User Session Repository.
 *
 * Features:
 * - Multi-device session management
 * - Session activity tracking
 * - Suspicious session detection
 * - Automatic cleanup
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findBySessionToken(String sessionToken);

    @Query("SELECT s FROM UserSession s WHERE s.sessionToken = :token AND s.status = 'ACTIVE' AND s.expiresAt > :now")
    Optional<UserSession> findActiveSession(@Param("token") String token, @Param("now") LocalDateTime now);

    List<UserSession> findByUserId(UUID userId);

    List<UserSession> findByUserIdAndStatus(UUID userId, UserSession.SessionStatus status);

    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.status = 'ACTIVE' AND s.expiresAt > :now")
    List<UserSession> findActiveSessionsByUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.user.id = :userId AND s.status = 'ACTIVE' AND s.expiresAt > :now")
    Long countActiveSessionsByUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    // Device-based queries
    List<UserSession> findByUserIdAndDeviceId(UUID userId, String deviceId);

    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.deviceId = :deviceId AND s.status = 'ACTIVE' AND s.expiresAt > :now")
    List<UserSession> findActiveSessionsByUserAndDevice(@Param("userId") UUID userId, @Param("deviceId") String deviceId, @Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT s.deviceType, COUNT(s) FROM UserSession s WHERE s.user.id = :userId AND s.status = 'ACTIVE' AND s.expiresAt > :now GROUP BY s.deviceType")
    List<Object[]> countActiveSessionsByDeviceType(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    // Security queries
    @Query("SELECT s FROM UserSession s WHERE s.isSuspicious = true AND s.status = 'ACTIVE'")
    List<UserSession> findSuspiciousSessions();

    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.isSuspicious = true")
    List<UserSession> findSuspiciousSessionsByUser(@Param("userId") UUID userId);

    @Query("SELECT s FROM UserSession s WHERE s.trustedDevice = false AND s.status = 'ACTIVE' AND s.expiresAt > :now")
    List<UserSession> findUntrustedDeviceSessions(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.ipAddress = :ipAddress AND s.status = 'ACTIVE' AND s.expiresAt > :now")
    List<UserSession> findSessionsByUserAndIp(@Param("userId") UUID userId, @Param("ipAddress") String ipAddress, @Param("now") LocalDateTime now);

    // Activity queries
    @Query("SELECT s FROM UserSession s WHERE s.lastActivityAt < :cutoffTime AND s.status = 'ACTIVE'")
    List<UserSession> findInactiveSessions(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT s FROM UserSession s WHERE s.expiresAt < :now AND s.status = 'ACTIVE'")
    List<UserSession> findExpiredSessions(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId ORDER BY s.lastActivityAt DESC")
    Page<UserSession> findSessionHistoryByUser(@Param("userId") UUID userId, Pageable pageable);

    // Bulk operations
    @Modifying
    @Query("UPDATE UserSession s SET s.status = 'TERMINATED', s.terminatedAt = :now, s.terminationReason = :reason WHERE s.user.id = :userId AND s.status = 'ACTIVE'")
    void terminateAllUserSessions(@Param("userId") UUID userId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    @Modifying
    @Query("UPDATE UserSession s SET s.status = 'TERMINATED', s.terminatedAt = :now, s.terminationReason = :reason WHERE s.user.id = :userId AND s.deviceId = :deviceId AND s.status = 'ACTIVE'")
    void terminateUserDeviceSessions(@Param("userId") UUID userId, @Param("deviceId") String deviceId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    @Modifying
    @Query("UPDATE UserSession s SET s.status = 'EXPIRED', s.terminatedAt = :now WHERE s.expiresAt < :now AND s.status = 'ACTIVE'")
    int expireInactiveSessions(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.isSuspicious = true, s.suspiciousReason = :reason WHERE s.id = :sessionId")
    void markSessionAsSuspicious(@Param("sessionId") UUID sessionId, @Param("reason") String reason);

    // Cleanup operations
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.status IN ('TERMINATED', 'EXPIRED') AND s.terminatedAt < :cutoffDate")
    void deleteOldTerminatedSessions(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.user.id IN (SELECT u.id FROM User u WHERE u.deleted = true)")
    void deleteSessionsForDeletedUsers();

    // Statistics
    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.status = 'ACTIVE' AND s.expiresAt > :now")
    Long countActiveSessions(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.createdAt >= :since")
    Long countSessionsCreatedSince(@Param("since") LocalDateTime since);

    @Query("SELECT s.deviceType, COUNT(s) FROM UserSession s WHERE s.status = 'ACTIVE' AND s.expiresAt > :now GROUP BY s.deviceType")
    List<Object[]> countActiveSessionsByDevice(@Param("now") LocalDateTime now);

    @Query("SELECT DATE(s.createdAt), COUNT(s) FROM UserSession s WHERE s.createdAt >= :since GROUP BY DATE(s.createdAt) ORDER BY DATE(s.createdAt)")
    List<Object[]> getSessionCreationTrend(@Param("since") LocalDateTime since);
}
