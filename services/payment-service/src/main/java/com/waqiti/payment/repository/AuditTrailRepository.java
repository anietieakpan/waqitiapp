package com.waqiti.payment.repository;

import com.waqiti.payment.entity.AuditTrail;
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
 * Repository for Payment Service Audit Trail operations
 */
@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, UUID> {

    /**
     * Find audit entries by entity ID and type
     */
    List<AuditTrail> findByEntityIdAndEntityTypeOrderByPerformedAtDesc(
        String entityId, String entityType);

    /**
     * Find audit entries by entity type
     */
    Page<AuditTrail> findByEntityType(String entityType, Pageable pageable);

    /**
     * Find audit entries by action
     */
    List<AuditTrail> findByActionOrderByPerformedAtDesc(String action);

    /**
     * Find audit entries by performer
     */
    Page<AuditTrail> findByPerformedByOrderByPerformedAtDesc(UUID performedBy, Pageable pageable);

    /**
     * Find audit entries within date range
     */
    Page<AuditTrail> findByPerformedAtBetweenOrderByPerformedAtDesc(
        LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find high severity audit entries
     */
    @Query("SELECT a FROM AuditTrail a WHERE a.severity IN ('HIGH', 'CRITICAL') " +
           "ORDER BY a.performedAt DESC")
    List<AuditTrail> findHighSeverityEntries();

    /**
     * Find non-compliant audit entries
     */
    List<AuditTrail> findByCompliantFalseOrderByPerformedAtDesc();

    /**
     * Find entries ready for archival
     */
    @Query("SELECT a FROM AuditTrail a WHERE a.archived = false " +
           "AND a.retentionRequired = false " +
           "AND a.performedAt < :cutoffDate")
    List<AuditTrail> findEntriesReadyForArchival(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find recent audit entries for monitoring
     */
    @Query("SELECT a FROM AuditTrail a ORDER BY a.performedAt DESC")
    List<AuditTrail> findRecentEntries(Pageable pageable);

    /**
     * Count audit entries by action and date range
     */
    @Query("SELECT COUNT(a) FROM AuditTrail a WHERE a.action = :action " +
           "AND a.performedAt BETWEEN :startDate AND :endDate")
    Long countByActionAndDateRange(
        @Param("action") String action,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Search audit entries - FIXED SQL injection vulnerability
     * Uses proper escaping of wildcards to prevent SQL injection through search terms
     */
    @Query("SELECT a FROM AuditTrail a WHERE " +
           "LOWER(a.entityId) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:searchTerm, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\_'), '%')) OR " +
           "LOWER(a.action) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:searchTerm, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\_'), '%')) OR " +
           "LOWER(a.newValues) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:searchTerm, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\_'), '%')) OR " +
           "LOWER(a.oldValues) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:searchTerm, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\_'), '%'))")
    Page<AuditTrail> searchEntries(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find audit entries by checksum (for integrity verification)
     */
    Optional<AuditTrail> findByChecksum(String checksum);

    /**
     * Mark entries as archived
     */
    @Modifying
    @Query("UPDATE AuditTrail a SET a.archived = true, a.archivedAt = :archivedAt " +
           "WHERE a.auditId IN :auditIds")
    void markAsArchived(@Param("auditIds") List<UUID> auditIds, 
                       @Param("archivedAt") LocalDateTime archivedAt);

    /**
     * Get audit statistics for dashboard
     */
    @Query(value = """
        SELECT 
            entity_type,
            action,
            severity,
            COUNT(*) as entry_count,
            COUNT(CASE WHEN compliant = true THEN 1 END) as compliant_count,
            COUNT(CASE WHEN severity IN ('HIGH', 'CRITICAL') THEN 1 END) as high_severity_count
        FROM payment_audit_trail 
        WHERE performed_at BETWEEN :startDate AND :endDate
        GROUP BY entity_type, action, severity
        ORDER BY entry_count DESC
        """, nativeQuery = true)
    List<Object[]> getAuditStatistics(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Find ledger transaction audit trail
     */
    @Query("SELECT a FROM AuditTrail a WHERE a.entityType = 'LEDGER_TRANSACTION' " +
           "AND a.entityId = :transactionId ORDER BY a.performedAt ASC")
    List<AuditTrail> findLedgerTransactionAuditTrail(@Param("transactionId") String transactionId);

    /**
     * Find failed transaction audit entries
     */
    @Query("SELECT a FROM AuditTrail a WHERE a.action = 'FAIL' " +
           "AND a.performedAt >= :since ORDER BY a.performedAt DESC")
    List<AuditTrail> findFailedTransactionsSince(@Param("since") LocalDateTime since);

    /**
     * Count audit entries by severity and date
     */
    @Query("SELECT a.severity, COUNT(a) FROM AuditTrail a " +
           "WHERE a.performedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY a.severity")
    List<Object[]> countBySeverityAndDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Find audit entries by IP address (for security monitoring)
     */
    List<AuditTrail> findByIpAddressOrderByPerformedAtDesc(String ipAddress);

    /**
     * Find suspicious audit patterns
     */
    @Query("SELECT a FROM AuditTrail a WHERE " +
           "(a.action = 'FAIL' AND a.severity IN ('HIGH', 'CRITICAL')) OR " +
           "(a.compliant = false) OR " +
           "(a.action IN ('DELETE', 'REVERSE') AND a.severity = 'CRITICAL') " +
           "ORDER BY a.performedAt DESC")
    List<AuditTrail> findSuspiciousEntries();

    /**
     * Get daily audit summary
     */
    @Query(value = """
        SELECT 
            DATE(performed_at) as audit_date,
            COUNT(*) as total_entries,
            COUNT(CASE WHEN severity IN ('HIGH', 'CRITICAL') THEN 1 END) as high_severity,
            COUNT(CASE WHEN compliant = false THEN 1 END) as non_compliant
        FROM payment_audit_trail 
        WHERE performed_at BETWEEN :startDate AND :endDate
        GROUP BY DATE(performed_at)
        ORDER BY audit_date DESC
        """, nativeQuery = true)
    List<Object[]> getDailyAuditSummary(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}