package com.waqiti.crypto.lightning.repository;

import com.waqiti.crypto.lightning.entity.AuditSeverity;
import com.waqiti.crypto.lightning.entity.LightningAuditEntity;
import com.waqiti.crypto.lightning.entity.LightningAuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Lightning audit log entities
 */
@Repository
public interface LightningAuditRepository extends JpaRepository<LightningAuditEntity, String> {

    /**
     * Find audit events by user ID
     */
    List<LightningAuditEntity> findByUserId(String userId);

    /**
     * Find audit events by user ID with pagination
     */
    Page<LightningAuditEntity> findByUserId(String userId, Pageable pageable);

    /**
     * Find audit events by event type
     */
    List<LightningAuditEntity> findByEventType(LightningAuditEventType eventType);

    /**
     * Find audit events by severity
     */
    List<LightningAuditEntity> findBySeverity(AuditSeverity severity);

    /**
     * Find audit events by user and event type
     */
    List<LightningAuditEntity> findByUserIdAndEventType(String userId, LightningAuditEventType eventType);

    /**
     * Find audit events within time range
     */
    List<LightningAuditEntity> findByTimestampBetween(Instant start, Instant end);

    /**
     * Find audit events by user within time range
     */
    List<LightningAuditEntity> findByUserIdAndTimestampBetween(
        String userId, Instant start, Instant end);

    /**
     * Find high severity events
     */
    @Query("SELECT a FROM LightningAuditEntity a WHERE a.severity IN ('HIGH', 'CRITICAL') " +
           "ORDER BY a.timestamp DESC")
    List<LightningAuditEntity> findHighSeverityEvents(Pageable pageable);

    /**
     * Find security events
     */
    @Query("SELECT a FROM LightningAuditEntity a WHERE a.eventType = 'SECURITY_EVENT' " +
           "ORDER BY a.timestamp DESC")
    List<LightningAuditEntity> findSecurityEvents(Pageable pageable);

    /**
     * Find events requiring compliance review
     */
    @Query("SELECT a FROM LightningAuditEntity a WHERE a.reviewedBy IS NULL " +
           "AND (a.severity = 'HIGH' OR a.eventType IN ('PAYMENT_SENT', 'INVOICE_SETTLED')) " +
           "ORDER BY a.timestamp DESC")
    List<LightningAuditEntity> findEventsRequiringReview();

    /**
     * Find critical events older than specified date for archival
     */
    @Query("SELECT a FROM LightningAuditEntity a WHERE a.timestamp < :cutoffDate " +
           "AND a.severity IN ('HIGH', 'CRITICAL') AND a.archived = false")
    List<LightningAuditEntity> findCriticalEventsOlderThan(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Find events by correlation ID
     */
    List<LightningAuditEntity> findByCorrelationId(String correlationId);

    /**
     * Find events by session ID
     */
    List<LightningAuditEntity> findBySessionId(String sessionId);

    /**
     * Count events by user and event type
     */
    long countByUserIdAndEventType(String userId, LightningAuditEventType eventType);

    /**
     * Count events by event type within time range
     */
    @Query("SELECT COUNT(a) FROM LightningAuditEntity a WHERE a.eventType = :eventType " +
           "AND a.timestamp >= :since")
    long countByEventTypeSince(@Param("eventType") LightningAuditEventType eventType, 
                               @Param("since") Instant since);

    /**
     * Count failed authentication attempts for user
     */
    @Query("SELECT COUNT(a) FROM LightningAuditEntity a WHERE a.userId = :userId " +
           "AND a.eventType = 'AUTHENTICATION' " +
           "AND JSON_EXTRACT(a.auditData, '$.success') = false " +
           "AND a.timestamp >= :since")
    long countFailedAuthenticationAttempts(@Param("userId") String userId, 
                                           @Param("since") Instant since);

    /**
     * Get audit statistics by event type
     */
    @Query("SELECT a.eventType, COUNT(a) FROM LightningAuditEntity a " +
           "GROUP BY a.eventType ORDER BY COUNT(a) DESC")
    List<Object[]> getAuditStatistics();

    /**
     * Get audit statistics by user
     */
    @Query("SELECT a.eventType, COUNT(a) FROM LightningAuditEntity a " +
           "WHERE a.userId = :userId GROUP BY a.eventType")
    List<Object[]> getUserAuditStatistics(@Param("userId") String userId);

    /**
     * Get daily audit counts
     */
    @Query("SELECT DATE(a.timestamp), a.eventType, COUNT(a) " +
           "FROM LightningAuditEntity a WHERE a.timestamp >= :since " +
           "GROUP BY DATE(a.timestamp), a.eventType " +
           "ORDER BY DATE(a.timestamp) DESC")
    List<Object[]> getDailyAuditCounts(@Param("since") Instant since);

    /**
     * Find suspicious activity patterns
     */
    @Query("SELECT a.userId, COUNT(a) FROM LightningAuditEntity a " +
           "WHERE a.eventType = 'PAYMENT_FAILED' AND a.timestamp >= :since " +
           "GROUP BY a.userId HAVING COUNT(a) > :threshold " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> findSuspiciousPaymentPatterns(@Param("since") Instant since, 
                                                  @Param("threshold") long threshold);

    /**
     * Find users with high activity
     */
    @Query("SELECT a.userId, COUNT(a) FROM LightningAuditEntity a " +
           "WHERE a.timestamp >= :since " +
           "GROUP BY a.userId HAVING COUNT(a) > :threshold " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> findHighActivityUsers(@Param("since") Instant since, 
                                         @Param("threshold") long threshold);

    /**
     * Delete old audit records (respecting retention period)
     */
    @Modifying
    @Query("DELETE FROM LightningAuditEntity a WHERE a.timestamp < :cutoffDate " +
           "AND a.severity NOT IN ('HIGH', 'CRITICAL') AND a.archived = false")
    int deleteOldAuditRecords(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Mark events as archived
     */
    @Modifying
    @Query("UPDATE LightningAuditEntity a SET a.archived = true WHERE a.timestamp < :cutoffDate " +
           "AND a.severity IN ('HIGH', 'CRITICAL')")
    int markEventsAsArchived(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Find events by IP address
     */
    List<LightningAuditEntity> findByIpAddress(String ipAddress);

    /**
     * Find events with compliance issues
     */
    @Query("SELECT a FROM LightningAuditEntity a WHERE a.isCompliant = false " +
           "ORDER BY a.timestamp DESC")
    List<LightningAuditEntity> findComplianceIssues();

    /**
     * Update review status
     */
    @Modifying
    @Query("UPDATE LightningAuditEntity a SET a.reviewedBy = :reviewedBy, " +
           "a.reviewedAt = :reviewedAt WHERE a.id = :auditId")
    int updateReviewStatus(@Param("auditId") String auditId, 
                           @Param("reviewedBy") String reviewedBy, 
                           @Param("reviewedAt") Instant reviewedAt);

    /**
     * Find recent events for real-time monitoring
     */
    @Query("SELECT a FROM LightningAuditEntity a WHERE a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<LightningAuditEntity> findRecentEvents(@Param("since") Instant since, Pageable pageable);

    /**
     * Search audit events by description
     */
    @Query("SELECT a FROM LightningAuditEntity a WHERE LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY a.timestamp DESC")
    List<LightningAuditEntity> searchByDescription(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find events by source system
     */
    List<LightningAuditEntity> findBySourceSystem(String sourceSystem);

    /**
     * Count events by severity
     */
    long countBySeverity(AuditSeverity severity);

    /**
     * Find top event types by frequency
     */
    @Query("SELECT a.eventType, COUNT(a) as freq FROM LightningAuditEntity a " +
           "WHERE a.timestamp >= :since GROUP BY a.eventType " +
           "ORDER BY freq DESC")
    List<Object[]> findTopEventTypes(@Param("since") Instant since, Pageable pageable);
}