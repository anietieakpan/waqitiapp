package com.waqiti.gdpr.repository;

import com.waqiti.gdpr.domain.AuditAction;
import com.waqiti.gdpr.domain.AuditResult;
import com.waqiti.gdpr.domain.PrivacyAuditEvent;
import com.waqiti.gdpr.domain.PrivacyRight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for PrivacyAuditEvent entities
 * Maintains comprehensive audit trail for GDPR accountability
 */
@Repository
public interface PrivacyAuditEventRepository extends JpaRepository<PrivacyAuditEvent, String> {

    /**
     * Find audit events by event type
     */
    List<PrivacyAuditEvent> findByEventType(String eventType);

    /**
     * Find audit events for specific entity
     */
    List<PrivacyAuditEvent> findByEntityTypeAndEntityIdOrderByTimestampDesc(
        String entityType, String entityId);

    /**
     * Find audit events for specific user
     */
    List<PrivacyAuditEvent> findByUserIdOrderByTimestampDesc(String userId);

    /**
     * Find audit events by action
     */
    List<PrivacyAuditEvent> findByAction(AuditAction action);

    /**
     * Find audit events by privacy right
     */
    List<PrivacyAuditEvent> findByPrivacyRight(PrivacyRight privacyRight);

    /**
     * Find audit events in time range
     */
    List<PrivacyAuditEvent> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find audit events by correlation ID
     */
    List<PrivacyAuditEvent> findByCorrelationIdOrderByTimestampAsc(String correlationId);

    /**
     * Find failed audit events
     */
    List<PrivacyAuditEvent> findByResult(AuditResult result);

    /**
     * Find audit events by performer
     */
    List<PrivacyAuditEvent> findByPerformedByOrderByTimestampDesc(String performedBy);

    /**
     * Find recent audit events
     */
    @Query("SELECT a FROM PrivacyAuditEvent a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<PrivacyAuditEvent> findRecentEvents(@Param("since") LocalDateTime since);

    /**
     * Find expired audit events (for retention cleanup)
     */
    List<PrivacyAuditEvent> findByExpiresAtBefore(LocalDateTime expiryDate);

    /**
     * Count audit events before timestamp (for retention reporting)
     */
    long countByTimestampBefore(LocalDateTime timestamp);

    /**
     * Delete expired audit events
     */
    @Modifying
    @Query("DELETE FROM PrivacyAuditEvent a WHERE a.expiresAt < :expiryDate")
    int deleteByTimestampBefore(@Param("expiryDate") LocalDateTime expiryDate);

    /**
     * Find audit events for data subject request
     */
    @Query("SELECT a FROM PrivacyAuditEvent a WHERE a.entityType = 'DataSubjectRequest' " +
           "AND a.entityId = :requestId ORDER BY a.timestamp ASC")
    List<PrivacyAuditEvent> findByDataSubjectRequestId(@Param("requestId") String requestId);

    /**
     * Find audit events for data breach
     */
    @Query("SELECT a FROM PrivacyAuditEvent a WHERE a.entityType = 'DataBreach' " +
           "AND a.entityId = :breachId ORDER BY a.timestamp ASC")
    List<PrivacyAuditEvent> findByDataBreachId(@Param("breachId") String breachId);

    /**
     * Count events by action in time range
     */
    @Query("SELECT COUNT(a) FROM PrivacyAuditEvent a WHERE a.action = :action " +
           "AND a.timestamp BETWEEN :start AND :end")
    long countByActionAndTimestampBetween(
        @Param("action") AuditAction action,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    /**
     * Find events by GDPR article
     */
    List<PrivacyAuditEvent> findByGdprArticleOrderByTimestampDesc(String gdprArticle);

    /**
     * Find events from specific IP address (security investigation)
     */
    List<PrivacyAuditEvent> findByIpAddressOrderByTimestampDesc(String ipAddress);

    /**
     * Find events by session ID
     */
    List<PrivacyAuditEvent> findBySessionIdOrderByTimestampAsc(String sessionId);

    /**
     * Count total audit events
     */
    @Query("SELECT COUNT(a) FROM PrivacyAuditEvent a")
    long countAllAuditEvents();

    /**
     * Find audit events with errors
     */
    @Query("SELECT a FROM PrivacyAuditEvent a WHERE a.errorMessage IS NOT NULL " +
           "ORDER BY a.timestamp DESC")
    List<PrivacyAuditEvent> findEventsWithErrors();
}
