package com.waqiti.security.repository;

import com.waqiti.security.domain.SecurityEvent;
import com.waqiti.security.domain.SecurityEventType;
import com.waqiti.security.domain.SecuritySeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * High-performance repository for security events with optimized queries for audit trails,
 * monitoring, and analytics. Supports high-throughput event ingestion and efficient
 * querying for security operations and compliance reporting.
 * 
 * Performance Features:
 * - Batch insert operations for high-volume events
 * - Indexed queries for fast retrieval
 * - Streaming results for large datasets
 * - Partitioned queries for time-based data
 * - Optimized aggregation queries
 */
@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {
    
    // ===== BASIC QUERIES =====
    
    /**
     * Find events by user ID with pagination
     */
    Page<SecurityEvent> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);
    
    /**
     * Find events by event type
     */
    List<SecurityEvent> findByEventTypeOrderByTimestampDesc(SecurityEventType eventType);
    
    /**
     * Find events by severity level
     */
    List<SecurityEvent> findBySeverityOrderByTimestampDesc(SecuritySeverity severity);
    
    /**
     * Find events by source system
     */
    List<SecurityEvent> findBySourceSystemOrderByTimestampDesc(String sourceSystem);
    
    // ===== TIME-BASED QUERIES =====
    
    /**
     * Find events within time range - high performance with index
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.timestamp BETWEEN :startTime AND :endTime ORDER BY se.timestamp DESC")
    Page<SecurityEvent> findEventsByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
    
    /**
     * Find events within time range for specific user
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.userId = :userId " +
           "AND se.timestamp BETWEEN :startTime AND :endTime ORDER BY se.timestamp DESC")
    List<SecurityEvent> findUserEventsByTimeRange(
            @Param("userId") UUID userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find recent events (last N hours) - optimized for monitoring dashboards
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.timestamp >= :since ORDER BY se.timestamp DESC")
    Slice<SecurityEvent> findRecentEvents(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Stream events for large dataset processing
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.timestamp BETWEEN :startTime AND :endTime")
    java.util.stream.Stream<SecurityEvent> streamEventsByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    // ===== SEVERITY AND PRIORITY QUERIES =====
    
    /**
     * Find critical and high severity events
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.severity IN ('CRITICAL', 'HIGH') " +
           "AND se.timestamp >= :since ORDER BY se.severity DESC, se.timestamp DESC")
    List<SecurityEvent> findHighSeverityEvents(@Param("since") LocalDateTime since);
    
    /**
     * Find unresolved critical events
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.severity = 'CRITICAL' " +
           "AND (se.resolved = false OR se.resolved IS NULL) ORDER BY se.timestamp DESC")
    List<SecurityEvent> findUnresolvedCriticalEvents();
    
    /**
     * Find events requiring investigation
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.requiresInvestigation = true " +
           "AND (se.investigated = false OR se.investigated IS NULL) ORDER BY se.timestamp DESC")
    List<SecurityEvent> findEventsRequiringInvestigation();
    
    // ===== CORRELATION AND PATTERN QUERIES =====
    
    /**
     * Find events by correlation ID for transaction tracing
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.correlationId = :correlationId ORDER BY se.timestamp ASC")
    List<SecurityEvent> findByCorrelationId(@Param("correlationId") String correlationId);
    
    /**
     * Find related events by session ID
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.sessionId = :sessionId ORDER BY se.timestamp ASC")
    List<SecurityEvent> findBySessionId(@Param("sessionId") String sessionId);
    
    /**
     * Find events by transaction ID
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.transactionId = :transactionId ORDER BY se.timestamp ASC")
    List<SecurityEvent> findByTransactionId(@Param("transactionId") UUID transactionId);
    
    /**
     * Find events by IP address for security analysis
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.ipAddress = :ipAddress " +
           "AND se.timestamp >= :since ORDER BY se.timestamp DESC")
    List<SecurityEvent> findByIpAddress(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since
    );
    
    // ===== AGGREGATION AND ANALYTICS QUERIES =====
    
    /**
     * Count events by type within time range
     */
    @Query("SELECT se.eventType, COUNT(se) FROM SecurityEvent se " +
           "WHERE se.timestamp BETWEEN :startTime AND :endTime GROUP BY se.eventType")
    List<Object[]> countEventsByType(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Count events by severity within time range
     */
    @Query("SELECT se.severity, COUNT(se) FROM SecurityEvent se " +
           "WHERE se.timestamp BETWEEN :startTime AND :endTime GROUP BY se.severity")
    List<Object[]> countEventsBySeverity(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Count events by source system within time range
     */
    @Query("SELECT se.sourceSystem, COUNT(se) FROM SecurityEvent se " +
           "WHERE se.timestamp BETWEEN :startTime AND :endTime GROUP BY se.sourceSystem")
    List<Object[]> countEventsBySourceSystem(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get hourly event counts for time series analysis
     */
    @Query("SELECT FUNCTION('DATE_TRUNC', 'hour', se.timestamp) as hour, COUNT(se) " +
           "FROM SecurityEvent se WHERE se.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY FUNCTION('DATE_TRUNC', 'hour', se.timestamp) ORDER BY hour")
    List<Object[]> getHourlyEventCounts(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get top users by event count (for anomaly detection)
     */
    @Query("SELECT se.userId, COUNT(se) as eventCount FROM SecurityEvent se " +
           "WHERE se.timestamp >= :since GROUP BY se.userId " +
           "ORDER BY eventCount DESC")
    List<Object[]> getTopUsersByEventCount(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Get top IP addresses by event count (for security monitoring)
     */
    @Query("SELECT se.ipAddress, COUNT(se) as eventCount FROM SecurityEvent se " +
           "WHERE se.timestamp >= :since AND se.ipAddress IS NOT NULL " +
           "GROUP BY se.ipAddress ORDER BY eventCount DESC")
    List<Object[]> getTopIpsByEventCount(@Param("since") LocalDateTime since, Pageable pageable);
    
    // ===== ADVANCED QUERIES FOR THREAT DETECTION =====
    
    /**
     * Find suspicious activity patterns (high event frequency from single user)
     */
    @Query("SELECT se.userId, COUNT(se) as eventCount FROM SecurityEvent se " +
           "WHERE se.timestamp >= :since AND se.severity IN ('HIGH', 'CRITICAL') " +
           "GROUP BY se.userId HAVING COUNT(se) > :threshold ORDER BY eventCount DESC")
    List<Object[]> findSuspiciousUserActivity(
            @Param("since") LocalDateTime since,
            @Param("threshold") long threshold
    );
    
    /**
     * Find failed authentication attempts by IP
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.eventType = 'AUTHENTICATION_FAILED' " +
           "AND se.ipAddress = :ipAddress AND se.timestamp >= :since ORDER BY se.timestamp DESC")
    List<SecurityEvent> findFailedAuthenticationsByIp(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since
    );
    
    /**
     * Find potential brute force attacks (multiple failed attempts)
     */
    @Query("SELECT se.ipAddress, se.userId, COUNT(se) as attemptCount FROM SecurityEvent se " +
           "WHERE se.eventType = 'AUTHENTICATION_FAILED' AND se.timestamp >= :since " +
           "GROUP BY se.ipAddress, se.userId HAVING COUNT(se) >= :threshold")
    List<Object[]> findPotentialBruteForceAttacks(
            @Param("since") LocalDateTime since,
            @Param("threshold") long threshold
    );
    
    /**
     * Find events with similar patterns (for ML training data)
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.eventType = :eventType " +
           "AND se.severity = :severity AND se.timestamp >= :since")
    List<SecurityEvent> findSimilarEvents(
            @Param("eventType") SecurityEventType eventType,
            @Param("severity") SecuritySeverity severity,
            @Param("since") LocalDateTime since
    );
    
    // ===== COMPLIANCE AND AUDIT QUERIES =====
    
    /**
     * Find events for audit trail (ordered chronologically)
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.userId = :userId " +
           "AND se.timestamp BETWEEN :startTime AND :endTime " +
           "AND se.auditRequired = true ORDER BY se.timestamp ASC")
    List<SecurityEvent> findAuditTrail(
            @Param("userId") UUID userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find events for compliance reporting
     */
    @Query("SELECT se FROM SecurityEvent se WHERE se.complianceEvent = true " +
           "AND se.timestamp BETWEEN :startTime AND :endTime ORDER BY se.timestamp ASC")
    List<SecurityEvent> findComplianceEvents(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find unarchived events older than retention period
     */
    @Query("SELECT se.id FROM SecurityEvent se WHERE se.timestamp < :cutoffDate " +
           "AND (se.archived = false OR se.archived IS NULL)")
    List<UUID> findEventsForArchival(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ===== PERFORMANCE OPTIMIZATION QUERIES =====
    
    /**
     * Count total events (optimized for large tables)
     */
    @Query("SELECT COUNT(se.id) FROM SecurityEvent se")
    long countAllEvents();
    
    /**
     * Count events within time range (optimized)
     */
    @Query("SELECT COUNT(se) FROM SecurityEvent se WHERE se.timestamp BETWEEN :startTime AND :endTime")
    long countEventsByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Count unresolved events
     */
    @Query("SELECT COUNT(se) FROM SecurityEvent se WHERE se.resolved = false OR se.resolved IS NULL")
    long countUnresolvedEvents();
    
    /**
     * Get latest event timestamp (for incremental processing)
     */
    @Query("SELECT MAX(se.timestamp) FROM SecurityEvent se")
    Optional<LocalDateTime> findLatestEventTimestamp();
    
    /**
     * Get oldest event timestamp (for retention management)
     */
    @Query("SELECT MIN(se.timestamp) FROM SecurityEvent se WHERE se.archived = false OR se.archived IS NULL")
    Optional<LocalDateTime> findOldestUnarchivedEventTimestamp();
    
    // ===== BATCH OPERATIONS =====
    
    /**
     * Mark events as resolved (batch update)
     */
    @Modifying
    @Transactional
    @Query("UPDATE SecurityEvent se SET se.resolved = true, se.resolvedAt = :resolvedAt, " +
           "se.resolvedBy = :resolvedBy WHERE se.id IN :eventIds")
    int markEventsResolved(
            @Param("eventIds") List<UUID> eventIds,
            @Param("resolvedAt") LocalDateTime resolvedAt,
            @Param("resolvedBy") UUID resolvedBy
    );
    
    /**
     * Mark events as archived (batch update)
     */
    @Modifying
    @Transactional
    @Query("UPDATE SecurityEvent se SET se.archived = true, se.archivedAt = :archivedAt " +
           "WHERE se.id IN :eventIds")
    int markEventsArchived(
            @Param("eventIds") List<UUID> eventIds,
            @Param("archivedAt") LocalDateTime archivedAt
    );
    
    /**
     * Delete old archived events (data retention)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SecurityEvent se WHERE se.archived = true " +
           "AND se.archivedAt < :cutoffDate")
    int deleteArchivedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Update event priority (batch update)
     */
    @Modifying
    @Transactional
    @Query("UPDATE SecurityEvent se SET se.priority = :priority WHERE se.id IN :eventIds")
    int updateEventPriority(
            @Param("eventIds") List<UUID> eventIds,
            @Param("priority") Integer priority
    );
    
    // ===== CUSTOM SEARCH QUERIES =====
    
    /**
     * Search events by message content (full-text search if supported)
     */
    @Query("SELECT se FROM SecurityEvent se WHERE LOWER(se.message) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY se.timestamp DESC")
    Page<SecurityEvent> searchEventsByMessage(
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );
    
    /**
     * Search events by multiple criteria
     */
    @Query("SELECT se FROM SecurityEvent se WHERE " +
           "(:userId IS NULL OR se.userId = :userId) AND " +
           "(:eventType IS NULL OR se.eventType = :eventType) AND " +
           "(:severity IS NULL OR se.severity = :severity) AND " +
           "(:sourceSystem IS NULL OR se.sourceSystem = :sourceSystem) AND " +
           "se.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY se.timestamp DESC")
    Page<SecurityEvent> searchEventsWithCriteria(
            @Param("userId") UUID userId,
            @Param("eventType") SecurityEventType eventType,
            @Param("severity") SecuritySeverity severity,
            @Param("sourceSystem") String sourceSystem,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
    
    // ===== HEALTH CHECK QUERIES =====
    
    /**
     * Check if repository is healthy (recent events exist)
     */
    @Query("SELECT COUNT(se) > 0 FROM SecurityEvent se WHERE se.timestamp >= :since")
    boolean hasRecentEvents(@Param("since") LocalDateTime since);
    
    /**
     * Get database connection health (simple count query)
     */
    @Query("SELECT 1")
    int healthCheck();
}