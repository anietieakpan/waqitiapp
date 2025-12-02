package com.waqiti.common.gdpr.repository;

import com.waqiti.common.gdpr.model.GDPRAuditLog;
import com.waqiti.common.gdpr.enums.GDPRAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @deprecated This shared GDPR module is deprecated. Use the dedicated GDPR Service instead.
 * @see com.waqiti.gdpr.repository.RequestAuditLogRepository
 */
@Deprecated(since = "1.0-SNAPSHOT", forRemoval = true)
@Repository
public interface GDPRAuditLogRepository extends JpaRepository<GDPRAuditLog, UUID> {
    @Query("SELECT a FROM GDPRAuditLog a WHERE a.userId = :userId ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.actionType = :actionType ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByActionType(@Param("actionType") String actionType);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.userId = :userId AND a.actionType = :actionType ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByUserIdAndActionType(@Param("userId") UUID userId, @Param("actionType") String actionType);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByTimestampBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.userId = :userId AND a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByUserIdAndTimestampBetween(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByEntityTypeAndEntityId(@Param("entityType") String entityType, @Param("entityId") String entityId);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.performedBy = :performedBy ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByPerformedBy(@Param("performedBy") String performedBy);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.ipAddress = :ipAddress ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByIpAddress(@Param("ipAddress") String ipAddress);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.requestId = :requestId ORDER BY a.timestamp ASC")
    List<GDPRAuditLog> findByRequestId(@Param("requestId") String requestId);

    @Query("SELECT a.actionType, COUNT(a) FROM GDPRAuditLog a GROUP BY a.actionType")
    List<Object[]> countActionsByType();

    @Query("SELECT a.actionType, COUNT(a) FROM GDPRAuditLog a WHERE a.userId = :userId GROUP BY a.actionType")
    List<Object[]> countUserActionsByType(@Param("userId") UUID userId);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.userId = :userId AND a.actionType IN ('CONSENT_GRANTED', 'CONSENT_REVOKED', 'CONSENT_UPDATED') ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findConsentActions(@Param("userId") UUID userId);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.userId = :userId AND a.actionType IN ('DATA_EXPORT_REQUESTED', 'DATA_EXPORT_GENERATED', 'DATA_EXPORT_DOWNLOADED') ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findDataExportActions(@Param("userId") UUID userId);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.userId = :userId AND a.actionType IN ('DELETION_REQUESTED', 'DELETION_APPROVED', 'DELETION_COMPLETED', 'DATA_ANONYMIZED', 'DATA_HARD_DELETED') ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findDataDeletionActions(@Param("userId") UUID userId);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.actionType IN ('BREACH_DETECTED', 'BREACH_REPORTED_TO_AUTHORITY', 'BREACH_NOTIFIED_TO_USER') ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findBreachNotificationActions();

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.complianceArticle LIKE %:article% ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByComplianceArticle(@Param("article") String article);

    @Query("SELECT a.ipAddress, COUNT(a) FROM GDPRAuditLog a WHERE a.timestamp >= :since GROUP BY a.ipAddress HAVING COUNT(a) > :threshold ORDER BY COUNT(a) DESC")
    List<Object[]> findSuspiciousIpActivity(@Param("since") LocalDateTime since, @Param("threshold") long threshold);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findRecentActions(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM GDPRAuditLog a")
    long countTotalActions();

    @Query("SELECT COUNT(a) FROM GDPRAuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate")
    long countActionsBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT a FROM GDPRAuditLog a WHERE a.performedByType = :performerType ORDER BY a.timestamp DESC")
    List<GDPRAuditLog> findByPerformedByType(@Param("performerType") String performerType);

    @Query("SELECT a.actionType, COUNT(a) as count, MIN(a.timestamp) as firstOccurrence, MAX(a.timestamp) as lastOccurrence FROM GDPRAuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate GROUP BY a.actionType ORDER BY COUNT(a) DESC")
    List<Object[]> getActivitySummary(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}