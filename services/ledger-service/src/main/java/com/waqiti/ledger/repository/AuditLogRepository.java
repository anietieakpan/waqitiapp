package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for immutable audit log entries
 * 
 * Only supports read and create operations - no updates or deletes allowed
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {
    
    /**
     * Find the most recent audit log entry for hash chain
     */
    Optional<AuditLogEntry> findTopByOrderByTimestampDesc();
    
    /**
     * Find audit logs within a time range for verification
     */
    List<AuditLogEntry> findByTimestampBetweenOrderByTimestamp(
        LocalDateTime startTime, 
        LocalDateTime endTime
    );
    
    /**
     * Query audit logs with multiple filters
     */
    @Query("SELECT a FROM AuditLogEntry a WHERE " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:entityId IS NULL OR a.entityId = :entityId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:performedBy IS NULL OR a.performedBy = :performedBy) AND " +
           "(:startTime IS NULL OR a.timestamp >= :startTime) AND " +
           "(:endTime IS NULL OR a.timestamp <= :endTime) " +
           "ORDER BY a.timestamp DESC")
    List<AuditLogEntry> findByQuery(
        @Param("entityType") String entityType,
        @Param("entityId") String entityId,
        @Param("action") String action,
        @Param("performedBy") String performedBy,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find audit logs by entity
     */
    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByTimestampDesc(
        String entityType, 
        String entityId
    );
    
    /**
     * Find audit logs by user
     */
    List<AuditLogEntry> findByPerformedByOrderByTimestampDesc(String performedBy);
    
    /**
     * Find audit logs by correlation ID for distributed tracing
     */
    List<AuditLogEntry> findByCorrelationIdOrderByTimestamp(String correlationId);
    
    /**
     * Find failed operations for analysis
     */
    List<AuditLogEntry> findBySuccessfulFalseAndTimestampBetween(
        LocalDateTime startTime,
        LocalDateTime endTime
    );
    
    /**
     * Check if a hash exists (for duplicate detection)
     */
    boolean existsByHash(String hash);
    
    /**
     * Count audit logs by action and time range
     */
    @Query("SELECT COUNT(a) FROM AuditLogEntry a WHERE " +
           "a.action = :action AND " +
           "a.timestamp BETWEEN :startTime AND :endTime")
    long countByActionAndTimeRange(
        @Param("action") String action,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get audit statistics by entity type
     */
    @Query("SELECT a.entityType, COUNT(a) FROM AuditLogEntry a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.entityType")
    List<Object[]> getAuditStatsByEntityType(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find non-archived audit logs for operational queries
     */
    List<AuditLogEntry> findByArchivedFalseOrderByTimestampDesc();
    
    /**
     * Archive audit logs by date range (administrative function)
     */
    @Modifying
    @Query("UPDATE AuditLogEntry a SET a.archived = true, a.archivedAt = :archivedAt, " +
           "a.archivedReason = :reason, a.archivedBy = :archivedBy " +
           "WHERE a.timestamp < :cutoffDate AND a.archived = false")
    void archiveByDateRange(
        @Param("cutoffDate") LocalDateTime cutoffDate,
        @Param("archivedAt") LocalDateTime archivedAt,
        @Param("reason") String reason,
        @Param("archivedBy") String archivedBy
    );
    
    /**
     * Find archived entries by cutoff date for counting
     */
    List<AuditLogEntry> findByTimestampBeforeAndArchivedTrue(LocalDateTime cutoffDate);
}