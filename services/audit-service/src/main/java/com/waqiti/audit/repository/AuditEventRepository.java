package com.waqiti.audit.repository;

import com.waqiti.audit.model.AuditEvent;
import com.waqiti.audit.model.AuditSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import com.waqiti.audit.model.DailyEventCount;

/**
 * Industrial-grade audit event repository supporting high-volume operations
 * and time-series optimized queries for financial regulatory compliance.
 * 
 * Features:
 * - Time-series optimized queries for 1M+ events/hour
 * - Batch operations for high-performance data processing
 * - Compliance reporting and regulatory queries
 * - Real-time analytics and fraud detection queries
 * - Advanced indexing strategies for sub-second query performance
 * - Stream processing support for large result sets
 * - Partition-aware queries for horizontal scaling
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String>, 
                                            JpaSpecificationExecutor<AuditEvent> {

    // ========== HIGH-PERFORMANCE TIME-SERIES QUERIES ==========

    /**
     * Find events using time-series partition optimization
     * Optimized for queries on large datasets with timestamp-based partitioning
     */
    @Query(value = "SELECT * FROM audit_events " +
                   "WHERE timestamp >= :startDate AND timestamp <= :endDate " +
                   "ORDER BY timestamp DESC, id " +
                   "LIMIT :limit OFFSET :offset", 
           nativeQuery = true)
    List<AuditEvent> findByTimestampRangeOptimized(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    /**
     * Stream events for memory-efficient processing of large result sets
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp ASC")
    Stream<AuditEvent> streamByTimestampRange(
        @Param("startDate") Instant startDate, 
        @Param("endDate") Instant endDate
    );

    /**
     * Find events with cursor-based pagination for consistent results
     */
    Slice<AuditEvent> findByTimestampLessThanEqualOrderByTimestampDescIdDesc(
        Instant cursor, Pageable pageable);

    // ========== USER AND SESSION QUERIES ==========

    /**
     * Find user events with optimized indexing
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.userId = :userId AND a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    Page<AuditEvent> findByUserIdAndTimestampAfter(
        @Param("userId") String userId, 
        @Param("since") Instant since, 
        Pageable pageable
    );

    /**
     * Find user session events for session analysis
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.userId = :userId AND a.sessionId = :sessionId " +
           "ORDER BY a.timestamp ASC")
    List<AuditEvent> findByUserIdAndSessionIdOrderByTimestamp(
        @Param("userId") String userId, 
        @Param("sessionId") String sessionId
    );

    /**
     * Count active sessions for user
     */
    @Query("SELECT COUNT(DISTINCT a.sessionId) FROM AuditEvent a WHERE a.userId = :userId " +
           "AND a.timestamp >= :since AND a.sessionId IS NOT NULL")
    long countActiveSessionsByUser(@Param("userId") String userId, @Param("since") Instant since);

    // ========== TRANSACTION AND CORRELATION QUERIES ==========

    /**
     * Find complete transaction trail with all related events
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.transactionId = :transactionId OR " +
           "a.correlationId IN (SELECT DISTINCT ae.correlationId FROM AuditEvent ae WHERE ae.transactionId = :transactionId) " +
           "ORDER BY a.timestamp ASC")
    List<AuditEvent> findCompleteTransactionTrail(@Param("transactionId") String transactionId);

    /**
     * Find correlated events across services
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.correlationId = :correlationId ORDER BY a.timestamp ASC")
    List<AuditEvent> findByCorrelationIdOrderByTimestamp(@Param("correlationId") String correlationId);

    /**
     * Find orphaned correlation events (missing parent transaction)
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.correlationId IS NOT NULL " +
           "AND NOT EXISTS (SELECT 1 FROM AuditEvent ae WHERE ae.transactionId = a.correlationId) " +
           "AND a.timestamp >= :since")
    List<AuditEvent> findOrphanedCorrelationEvents(@Param("since") Instant since);

    // ========== COMPLIANCE AND REGULATORY QUERIES ==========

    /**
     * Find events by compliance framework with date range
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:complianceTag IS NULL OR a.complianceTags LIKE CONCAT('%', :complianceTag, '%')) " +
           "AND a.timestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY a.timestamp DESC")
    Page<AuditEvent> findByComplianceTagAndDateRange(
        @Param("complianceTag") String complianceTag,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        Pageable pageable
    );

    /**
     * Find SOX compliance events requiring reporting
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "a.complianceTags LIKE '%SOX%' AND " +
           "a.severity IN ('HIGH', 'CRITICAL', 'REGULATORY') AND " +
           "a.timestamp >= :reportingPeriodStart " +
           "ORDER BY a.timestamp DESC")
    List<AuditEvent> findSoxComplianceEvents(@Param("reportingPeriodStart") Instant reportingPeriodStart);

    /**
     * Find GDPR data access events for specific user
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "a.complianceTags LIKE '%GDPR%' AND " +
           "a.eventType = 'DATA_ACCESS' AND " +
           "a.userId = :userId AND " +
           "a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<AuditEvent> findGdprDataAccessEvents(
        @Param("userId") String userId, 
        @Param("since") Instant since
    );

    /**
     * Find PCI DSS payment processing events
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "a.complianceTags LIKE '%PCI_DSS%' AND " +
           "a.eventType IN ('FINANCIAL_TRANSACTION', 'PAYMENT_PROCESSING') AND " +
           "a.timestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY a.timestamp DESC")
    List<AuditEvent> findPciDssPaymentEvents(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    // ========== SECURITY AND FRAUD DETECTION QUERIES ==========

    /**
     * Find high-risk security events for real-time monitoring
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(a.severity IN ('CRITICAL', 'FRAUD') OR a.riskScore >= 75) AND " +
           "a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC, a.riskScore DESC")
    List<AuditEvent> findHighRiskSecurityEvents(@Param("since") Instant since);

    /**
     * Find failed authentication attempts by IP
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "a.eventType = 'AUTHENTICATION' AND " +
           "a.result IN ('UNAUTHORIZED', 'FORBIDDEN', 'FAILURE') AND " +
           "a.ipAddress = :ipAddress AND " +
           "a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<AuditEvent> findFailedAuthenticationsByIp(
        @Param("ipAddress") String ipAddress, 
        @Param("since") Instant since
    );

    /**
     * Find suspicious transaction patterns
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "a.eventType = 'FINANCIAL_TRANSACTION' AND " +
           "a.userId = :userId AND " +
           "(a.riskScore >= :riskThreshold OR a.severity = 'FRAUD') AND " +
           "a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<AuditEvent> findSuspiciousTransactions(
        @Param("userId") String userId,
        @Param("riskThreshold") Integer riskThreshold,
        @Param("since") Instant since
    );

    /**
     * Find velocity anomalies (unusual activity frequency)
     */
    @Query(value = "SELECT * FROM audit_events a WHERE " +
                   "a.user_id = :userId AND " +
                   "a.timestamp >= :windowStart AND " +
                   "a.timestamp <= :windowEnd " +
                   "GROUP BY user_id " +
                   "HAVING COUNT(*) > :threshold " +
                   "ORDER BY timestamp DESC", 
           nativeQuery = true)
    List<AuditEvent> findVelocityAnomalies(
        @Param("userId") String userId,
        @Param("windowStart") Instant windowStart,
        @Param("windowEnd") Instant windowEnd,
        @Param("threshold") Long threshold
    );

    // ========== ANALYTICS AND REPORTING QUERIES ==========

    /**
     * Get event count statistics by hour for dashboard
     */
    @Query(value = "SELECT " +
                   "DATE_TRUNC('hour', timestamp) as hour, " +
                   "COUNT(*) as event_count, " +
                   "COUNT(CASE WHEN result = 'SUCCESS' THEN 1 END) as success_count, " +
                   "COUNT(CASE WHEN result != 'SUCCESS' THEN 1 END) as failure_count " +
                   "FROM audit_events " +
                   "WHERE timestamp >= :startDate AND timestamp <= :endDate " +
                   "GROUP BY DATE_TRUNC('hour', timestamp) " +
                   "ORDER BY hour", 
           nativeQuery = true)
    List<Object[]> getEventStatsByHour(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    /**
     * Get top users by event volume
     */
    @Query("SELECT a.userId, COUNT(a) as eventCount FROM AuditEvent a WHERE " +
           "a.timestamp >= :since AND a.userId IS NOT NULL " +
           "GROUP BY a.userId ORDER BY COUNT(a) DESC")
    List<Object[]> getTopUsersByVolume(@Param("since") Instant since, Pageable pageable);

    /**
     * Get error pattern analysis
     */
    @Query("SELECT a.errorMessage, a.eventType, COUNT(a) as errorCount FROM AuditEvent a WHERE " +
           "a.result IN ('FAILURE', 'SYSTEM_ERROR') AND " +
           "a.timestamp >= :since AND " +
           "a.errorMessage IS NOT NULL " +
           "GROUP BY a.errorMessage, a.eventType " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> getErrorPatternAnalysis(@Param("since") Instant since);

    /**
     * Get service performance metrics
     */
    @Query("SELECT a.serviceName, " +
           "COUNT(a) as totalEvents, " +
           "AVG(a.durationMs) as avgDurationMs, " +
           "MAX(a.durationMs) as maxDurationMs, " +
           "COUNT(CASE WHEN a.result = 'SUCCESS' THEN 1 END) * 100.0 / COUNT(a) as successRate " +
           "FROM AuditEvent a WHERE " +
           "a.timestamp >= :since AND a.durationMs IS NOT NULL " +
           "GROUP BY a.serviceName " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> getServicePerformanceMetrics(@Param("since") Instant since);

    // ========== DATA MANAGEMENT AND ARCHIVAL ==========

    /**
     * Find events ready for archival based on retention policies
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "a.retentionDate <= :currentDate AND " +
           "a.archived = false AND " +
           "NOT EXISTS (SELECT 1 FROM AuditEvent ae WHERE ae.metadata LIKE '%legal_hold%:true%' AND ae.id = a.id)")
    List<AuditEvent> findEventsReadyForArchival(@Param("currentDate") Instant currentDate);

    /**
     * Find events under legal hold
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(a.metadata LIKE '%legal_hold%:true%' OR " +
           "a.complianceTags LIKE '%LEGAL_HOLD%') AND " +
           "a.archived = false")
    List<AuditEvent> findEventsUnderLegalHold();

    /**
     * Mark events as archived (batch operation)
     */
    @Modifying
    @Transactional
    @Query("UPDATE AuditEvent a SET a.archived = true WHERE a.id IN :eventIds")
    int markEventsAsArchived(@Param("eventIds") List<String> eventIds);

    /**
     * Delete archived events older than specified date
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AuditEvent a WHERE a.archived = true AND a.retentionDate <= :deleteDate")
    int deleteArchivedEventsOlderThan(@Param("deleteDate") Instant deleteDate);

    // ========== INTEGRITY AND CHAIN VERIFICATION ==========

    /**
     * Find events with integrity violations
     */
    @Query(value = "SELECT * FROM audit_events WHERE " +
                   "integrity_hash IS NULL OR " +
                   "LENGTH(integrity_hash) != 44", // Base64 SHA-256 is 44 characters
           nativeQuery = true)
    List<AuditEvent> findEventsWithIntegrityViolations();

    /**
     * Find chain integrity breaks
     */
    @Query(value = "SELECT a1.* FROM audit_events a1 " +
                   "LEFT JOIN audit_events a2 ON a1.previous_event_hash = a2.integrity_hash " +
                   "WHERE a1.previous_event_hash IS NOT NULL AND a2.id IS NULL " +
                   "ORDER BY a1.timestamp", 
           nativeQuery = true)
    List<AuditEvent> findChainIntegrityBreaks();

    /**
     * Get the latest event for chain continuation
     */
    @Query("SELECT a FROM AuditEvent a ORDER BY a.timestamp DESC, a.id DESC")
    Optional<AuditEvent> findLatestEvent(Pageable pageable);

    // ========== BATCH OPERATIONS FOR HIGH PERFORMANCE ==========

    /**
     * Batch insert support for high-volume ingestion
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO audit_events (id, event_type, service_name, timestamp, user_id, " +
                   "action, result, severity, compliance_tags, integrity_hash) " +
                   "VALUES (:id, :eventType, :serviceName, :timestamp, :userId, " +
                   ":action, :result, :severity, :complianceTags, :integrityHash)", 
           nativeQuery = true)
    void batchInsertAuditEvent(
        @Param("id") String id,
        @Param("eventType") String eventType,
        @Param("serviceName") String serviceName,
        @Param("timestamp") Instant timestamp,
        @Param("userId") String userId,
        @Param("action") String action,
        @Param("result") String result,
        @Param("severity") String severity,
        @Param("complianceTags") String complianceTags,
        @Param("integrityHash") String integrityHash
    );

    /**
     * Update risk scores in batch
     */
    @Modifying
    @Transactional
    @Query("UPDATE AuditEvent a SET a.riskScore = :riskScore WHERE a.id IN :eventIds")
    int updateRiskScoresInBatch(@Param("eventIds") List<String> eventIds, @Param("riskScore") Integer riskScore);

    // ========== PARTITION AND SCALING SUPPORT ==========

    /**
     * Find events by partition key for horizontal scaling
     */
    @Query(value = "SELECT * FROM audit_events WHERE " +
                   "DATE_TRUNC('day', timestamp) = :partitionDate " +
                   "ORDER BY timestamp, id", 
           nativeQuery = true)
    List<AuditEvent> findByPartitionDate(@Param("partitionDate") LocalDate partitionDate);

    /**
     * Get partition statistics for load balancing
     */
    @Query(value = "SELECT " +
                   "DATE_TRUNC('day', timestamp) as partition_date, " +
                   "COUNT(*) as event_count, " +
                   "MAX(timestamp) as latest_event, " +
                   "MIN(timestamp) as earliest_event " +
                   "FROM audit_events " +
                   "WHERE timestamp >= :startDate " +
                   "GROUP BY DATE_TRUNC('day', timestamp) " +
                   "ORDER BY partition_date", 
           nativeQuery = true)
    List<Object[]> getPartitionStatistics(@Param("startDate") Instant startDate);

    // ========== MONITORING AND HEALTH CHECKS ==========

    /**
     * Check repository health and performance
     */
    @Query(value = "SELECT COUNT(*) as total_events, " +
                   "COUNT(CASE WHEN timestamp >= NOW() - INTERVAL '1 hour' THEN 1 END) as recent_events, " +
                   "COUNT(CASE WHEN archived = true THEN 1 END) as archived_events, " +
                   "COUNT(CASE WHEN integrity_hash IS NULL THEN 1 END) as integrity_issues " +
                   "FROM audit_events", 
           nativeQuery = true)
    Object[] getRepositoryHealthStats();

    /**
     * Get ingestion rate for monitoring
     */
    @Query(value = "SELECT COUNT(*) FROM audit_events WHERE timestamp >= :since", 
           nativeQuery = true)
    long countEventsSince(@Param("since") Instant since);

    /**
     * Find duplicate events for data quality monitoring
     */
    @Query("SELECT a.integrityHash, COUNT(a) FROM AuditEvent a WHERE a.integrityHash IS NOT NULL " +
           "GROUP BY a.integrityHash HAVING COUNT(a) > 1")
    List<Object[]> findDuplicateEvents();

    // ========== CUSTOM SEARCH WITH DYNAMIC CRITERIA ==========

    /**
     * Advanced search with multiple optional criteria
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:serviceName IS NULL OR a.serviceName = :serviceName) AND " +
           "(:action IS NULL OR a.action LIKE CONCAT('%', :action, '%')) AND " +
           "(:result IS NULL OR a.result = :result) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:minRiskScore IS NULL OR a.riskScore >= :minRiskScore) AND " +
           "(:complianceTag IS NULL OR a.complianceTags LIKE CONCAT('%', :complianceTag, '%')) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) AND " +
           "(:ipAddress IS NULL OR a.ipAddress = :ipAddress) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditEvent> findByAdvancedCriteria(
        @Param("userId") String userId,
        @Param("eventType") String eventType,
        @Param("serviceName") String serviceName,
        @Param("action") String action,
        @Param("result") AuditEvent.AuditResult result,
        @Param("severity") AuditSeverity severity,
        @Param("minRiskScore") Integer minRiskScore,
        @Param("complianceTag") String complianceTag,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        @Param("ipAddress") String ipAddress,
        @Param("resourceType") String resourceType,
        Pageable pageable
    );

    // Additional methods for AuditService compatibility

    /**
     * Find events by criteria for search functionality
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:entityType IS NULL OR a.resourceType = :entityType) AND " +
           "(:entityId IS NULL OR a.resourceId = :entityId) AND " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) AND " +
           "(:severity IS NULL OR a.severity = :severity) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditEvent> findByCriteria(
        @Param("entityType") String entityType,
        @Param("entityId") String entityId,
        @Param("eventType") String eventType,
        @Param("userId") String userId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        @Param("severity") AuditSeverity severity,
        Pageable pageable
    );

    /**
     * Find by entity ID and type ordered by timestamp
     */
    List<AuditEvent> findByResourceIdAndResourceTypeOrderByTimestampAsc(String entityId, String entityType);
    
    /**
     * Alias for compatibility
     */
    default List<AuditEvent> findByEntityIdAndEntityTypeOrderByTimestampAsc(String entityId, String entityType) {
        return findByResourceIdAndResourceTypeOrderByTimestampAsc(entityId, entityType);
    }

    /**
     * Find by timestamp between for verification
     */
    List<AuditEvent> findByTimestampBetweenOrderByTimestampAsc(Instant startTime, Instant endTime);

    /**
     * Count events by timestamp range
     */
    long countByTimestampBetween(Instant start, Instant end);

    /**
     * Count events by event type and timestamp range
     */
    @Query("SELECT a.eventType, COUNT(a) FROM AuditEvent a WHERE a.timestamp BETWEEN :start AND :end GROUP BY a.eventType")
    List<Object[]> countByEventTypeAndTimestampBetween(@Param("start") Instant start, @Param("end") Instant end);
    
    default Map<String, Long> countByEventTypeAndTimestampBetween(Instant start, Instant end) {
        return countByEventTypeAndTimestampBetween(start, end).stream()
            .collect(java.util.stream.Collectors.toMap(
                arr -> (String) arr[0],
                arr -> (Long) arr[1]
            ));
    }

    /**
     * Count events by severity and timestamp range
     */
    @Query("SELECT a.severity, COUNT(a) FROM AuditEvent a WHERE a.timestamp BETWEEN :start AND :end GROUP BY a.severity")
    List<Object[]> countBySeverityAndTimestampBetween(@Param("start") Instant start, @Param("end") Instant end);
    
    default Map<String, Long> countBySeverityAndTimestampBetween(Instant start, Instant end) {
        return countBySeverityAndTimestampBetween(start, end).stream()
            .collect(java.util.stream.Collectors.toMap(
                arr -> arr[0] != null ? arr[0].toString() : "null",
                arr -> (Long) arr[1]
            ));
    }

    /**
     * Count events by entity type and timestamp range
     */
    @Query("SELECT a.resourceType, COUNT(a) FROM AuditEvent a WHERE a.timestamp BETWEEN :start AND :end GROUP BY a.resourceType")
    List<Object[]> countByEntityTypeAndTimestampBetween(@Param("start") Instant start, @Param("end") Instant end);
    
    default Map<String, Long> countByEntityTypeAndTimestampBetween(Instant start, Instant end) {
        return countByEntityTypeAndTimestampBetween(start, end).stream()
            .collect(java.util.stream.Collectors.toMap(
                arr -> (String) arr[0],
                arr -> (Long) arr[1]
            ));
    }

    /**
     * Get daily event counts for statistics
     */
    @Query(value = "SELECT DATE(timestamp) as date, COUNT(*) as count, " +
                   "COUNT(DISTINCT user_id) as unique_users, " +
                   "COUNT(DISTINCT service_name) as unique_services " +
                   "FROM audit_events WHERE timestamp BETWEEN :start AND :end " +
                   "GROUP BY DATE(timestamp) ORDER BY date", 
           nativeQuery = true)
    List<Object[]> getDailyEventCountsRaw(@Param("start") Instant start, @Param("end") Instant end);
    
    default List<DailyEventCount> getDailyEventCounts(Instant start, Instant end) {
        return getDailyEventCountsRaw(start, end).stream()
            .map(arr -> DailyEventCount.builder()
                .date(((java.sql.Date) arr[0]).toLocalDate())
                .count(((Number) arr[1]).longValue())
                .uniqueUsers(((Number) arr[2]).longValue())
                .uniqueServices(((Number) arr[3]).longValue())
                .build())
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Find events before timestamp for archiving
     */
    List<AuditEvent> findByTimestampBefore(Instant timestamp);

    /**
     * Delete events by timestamp before
     */
    @Modifying
    @Transactional
    void deleteByTimestampBefore(Instant timestamp);

    /**
     * Find the latest event for chain integrity
     */
    Optional<AuditEvent> findTopByOrderByTimestampDesc();
}