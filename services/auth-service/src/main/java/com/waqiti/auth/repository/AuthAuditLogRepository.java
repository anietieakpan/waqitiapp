package com.waqiti.auth.repository;

import com.waqiti.auth.domain.AuthAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise-grade Auth Audit Log Repository.
 *
 * Features:
 * - Immutable audit logs (no updates/deletes except archival)
 * - High-performance queries with indexes
 * - Compliance reporting support
 * - Forensic analysis queries
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID> {

    // User-specific queries
    List<AuthAuditLog> findByUserId(UUID userId);

    Page<AuthAuditLog> findByUserId(UUID userId, Pageable pageable);

    List<AuthAuditLog> findByUserIdAndEventType(UUID userId, AuthAuditLog.EventType eventType);

    @Query("SELECT a FROM AuthAuditLog a WHERE a.userId = :userId AND a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findUserAuditLog(@Param("userId") UUID userId,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate,
                                         Pageable pageable);

    // Event type queries
    List<AuthAuditLog> findByEventType(AuthAuditLog.EventType eventType);

    Page<AuthAuditLog> findByEventType(AuthAuditLog.EventType eventType, Pageable pageable);

    List<AuthAuditLog> findByEventTypeAndStatus(AuthAuditLog.EventType eventType, AuthAuditLog.EventStatus status);

    // Status queries
    List<AuthAuditLog> findByStatus(AuthAuditLog.EventStatus status);

    @Query("SELECT a FROM AuthAuditLog a WHERE a.status = 'SECURITY_ALERT' ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findSecurityAlerts();

    @Query("SELECT a FROM AuthAuditLog a WHERE a.status = 'SECURITY_ALERT' AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findRecentSecurityAlerts(@Param("since") LocalDateTime since);

    // Risk-based queries
    List<AuthAuditLog> findByRiskLevel(AuthAuditLog.RiskLevel riskLevel);

    @Query("SELECT a FROM AuthAuditLog a WHERE a.riskLevel IN ('HIGH', 'CRITICAL') AND a.createdAt >= :since ORDER BY a.riskLevel DESC, a.createdAt DESC")
    List<AuthAuditLog> findHighRiskEvents(@Param("since") LocalDateTime since);

    // IP and geolocation queries
    List<AuthAuditLog> findByIpAddress(String ipAddress);

    @Query("SELECT a FROM AuthAuditLog a WHERE a.userId = :userId AND a.ipAddress = :ipAddress ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findByUserAndIpAddress(@Param("userId") UUID userId, @Param("ipAddress") String ipAddress);

    @Query("SELECT a FROM AuthAuditLog a WHERE a.userId = :userId AND a.ipAddress != :currentIp AND a.createdAt >= :recentTime")
    List<AuthAuditLog> findLoginsFromDifferentIp(@Param("userId") UUID userId,
                                                   @Param("currentIp") String currentIp,
                                                   @Param("recentTime") LocalDateTime recentTime);

    // Failed login tracking
    @Query("SELECT a FROM AuthAuditLog a WHERE a.username = :username AND a.eventType = 'LOGIN_FAILURE' AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findRecentFailedLogins(@Param("username") String username, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuthAuditLog a WHERE a.username = :username AND a.eventType = 'LOGIN_FAILURE' AND a.createdAt >= :since")
    Long countRecentFailedLogins(@Param("username") String username, @Param("since") LocalDateTime since);

    @Query("SELECT a.ipAddress, COUNT(a) FROM AuthAuditLog a WHERE a.eventType = 'LOGIN_FAILURE' AND a.createdAt >= :since GROUP BY a.ipAddress HAVING COUNT(a) > :threshold")
    List<Object[]> findIpsWithMultipleFailedLogins(@Param("since") LocalDateTime since, @Param("threshold") Long threshold);

    // Session tracking
    List<AuthAuditLog> findBySessionId(UUID sessionId);

    @Query("SELECT a FROM AuthAuditLog a WHERE a.sessionId = :sessionId ORDER BY a.createdAt ASC")
    List<AuthAuditLog> findSessionTimeline(@Param("sessionId") UUID sessionId);

    // Time-based queries
    @Query("SELECT a FROM AuthAuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate,
                                        Pageable pageable);

    // Statistics and reporting
    @Query("SELECT a.eventType, COUNT(a) FROM AuthAuditLog a WHERE a.createdAt >= :since GROUP BY a.eventType")
    List<Object[]> getEventTypeStatistics(@Param("since") LocalDateTime since);

    @Query("SELECT a.status, COUNT(a) FROM AuthAuditLog a WHERE a.createdAt >= :since GROUP BY a.status")
    List<Object[]> getEventStatusStatistics(@Param("since") LocalDateTime since);

    @Query("SELECT DATE(a.createdAt), COUNT(a) FROM AuthAuditLog a WHERE a.eventType = 'LOGIN_SUCCESS' AND a.createdAt >= :since GROUP BY DATE(a.createdAt) ORDER BY DATE(a.createdAt)")
    List<Object[]> getLoginTrend(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuthAuditLog a WHERE a.eventType = :eventType AND a.createdAt >= :since")
    Long countEventsSince(@Param("eventType") AuthAuditLog.EventType eventType, @Param("since") LocalDateTime since);

    // Compliance reporting
    @Query("SELECT a FROM AuthAuditLog a WHERE a.userId = :userId AND a.createdAt >= :since AND a.eventType IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'PASSWORD_CHANGED', 'ROLE_ASSIGNED', 'PERMISSION_GRANTED') ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findComplianceReportData(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    // Correlation queries
    @Query("SELECT a FROM AuthAuditLog a WHERE a.correlationId = :correlationId ORDER BY a.createdAt ASC")
    List<AuthAuditLog> findRelatedEvents(@Param("correlationId") UUID correlationId);

    // Cleanup (for old data archival - should be rare)
    @Query("SELECT a FROM AuthAuditLog a WHERE a.createdAt < :cutoffDate")
    Page<AuthAuditLog> findLogsForArchival(@Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);
}
