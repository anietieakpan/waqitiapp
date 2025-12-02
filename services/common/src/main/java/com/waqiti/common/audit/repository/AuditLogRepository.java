package com.waqiti.common.audit.repository;

import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.domain.AuditLog;
import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;
import com.waqiti.common.audit.domain.AuditLog.OperationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for comprehensive audit log operations
 * 
 * Provides optimized queries for compliance reporting, forensic investigation,
 * and real-time monitoring of critical system events.
 * 
 * COMPLIANCE FEATURES:
 * - Immutable audit trail queries
 * - Compliance-specific filtering
 * - Retention policy management
 * - Fraud detection support
 * - Forensic investigation queries
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    
    // ========================================
    // BASIC SEARCH AND FILTERING
    // ========================================
    
    /**
     * Find audit logs by user ID with pagination
     */
    Page<AuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
    
    /**
     * Find audit logs by event type with pagination
     */
    Page<AuditLog> findByEventTypeOrderByTimestampDesc(AuditEventType eventType, Pageable pageable);
    
    /**
     * Find audit logs by event category
     */
    Page<AuditLog> findByEventCategoryOrderByTimestampDesc(EventCategory eventCategory, Pageable pageable);
    
    /**
     * Find audit logs by severity level
     */
    Page<AuditLog> findBySeverityOrderByTimestampDesc(Severity severity, Pageable pageable);
    
    /**
     * Find audit logs by operation result
     */
    Page<AuditLog> findByResultOrderByTimestampDesc(OperationResult result, Pageable pageable);
    
    // ========================================
    // TIME-BASED QUERIES
    // ========================================
    
    /**
     * Find audit logs within date range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :startDate AND a.timestamp <= :endDate ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTimestampBetween(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate, 
                                         Pageable pageable);
    
    /**
     * Find recent audit logs for a user
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentByUserId(@Param("userId") String userId, @Param("since") LocalDateTime since);
    
    /**
     * Find audit logs by session ID
     */
    List<AuditLog> findBySessionIdOrderByTimestampAsc(String sessionId);
    
    /**
     * Find audit logs by correlation ID
     */
    List<AuditLog> findByCorrelationIdOrderByTimestampAsc(String correlationId);
    
    // ========================================
    // COMPLIANCE-SPECIFIC QUERIES
    // ========================================
    
    /**
     * Find PCI DSS relevant audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.pciRelevant = true AND a.timestamp >= :startDate AND a.timestamp <= :endDate ORDER BY a.timestamp DESC")
    Page<AuditLog> findPciRelevantLogs(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate, 
                                      Pageable pageable);
    
    /**
     * Find GDPR relevant audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.gdprRelevant = true AND a.timestamp >= :startDate AND a.timestamp <= :endDate ORDER BY a.timestamp DESC")
    Page<AuditLog> findGdprRelevantLogs(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate, 
                                       Pageable pageable);
    
    /**
     * Find SOX relevant audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.soxRelevant = true AND a.timestamp >= :startDate AND a.timestamp <= :endDate ORDER BY a.timestamp DESC")
    Page<AuditLog> findSoxRelevantLogs(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate, 
                                      Pageable pageable);
    
    /**
     * Find SOC 2 relevant audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.soc2Relevant = true AND a.timestamp >= :startDate AND a.timestamp <= :endDate ORDER BY a.timestamp DESC")
    Page<AuditLog> findSoc2RelevantLogs(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate, 
                                       Pageable pageable);
    
    // ========================================
    // SECURITY AND FRAUD DETECTION
    // ========================================
    
    /**
     * Find failed authentication attempts by IP address
     */
    @Query("SELECT a FROM AuditLog a WHERE a.ipAddress = :ipAddress AND a.eventCategory = 'SECURITY' AND a.result = 'FAILURE' AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findFailedAuthenticationsByIp(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
    
    /**
     * Find suspicious activities by user
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND (a.eventCategory = 'FRAUD' OR a.riskScore > :riskThreshold) AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findSuspiciousActivitiesByUser(@Param("userId") String userId, 
                                                  @Param("riskThreshold") Integer riskThreshold, 
                                                  @Param("since") LocalDateTime since);
    
    /**
     * Find high-severity events requiring investigation
     */
    @Query("SELECT a FROM AuditLog a WHERE a.severity IN ('HIGH', 'CRITICAL', 'EMERGENCY') AND a.investigationRequired = true ORDER BY a.timestamp DESC")
    List<AuditLog> findEventsRequiringInvestigation();
    
    /**
     * Find events with fraud indicators
     */
    @Query("SELECT a FROM AuditLog a WHERE a.fraudIndicators IS NOT NULL AND a.fraudIndicators != '' ORDER BY a.timestamp DESC")
    List<AuditLog> findEventsWithFraudIndicators();
    
    // ========================================
    // FINANCIAL TRANSACTION AUDIT
    // ========================================
    
    /**
     * Find financial transaction audit trail
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventCategory = 'FINANCIAL' AND a.entityId = :transactionId ORDER BY a.timestamp ASC")
    List<AuditLog> findFinancialTransactionAuditTrail(@Param("transactionId") String transactionId);
    
    /**
     * Find large financial transactions
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT a FROM AuditLog a WHERE a.eventCategory = 'FINANCIAL' AND a.metadata LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:amountPattern, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') AND a.timestamp >= :startDate ORDER BY a.timestamp DESC")
    List<AuditLog> findLargeFinancialTransactions(@Param("amountPattern") String amountPattern, @Param("startDate") LocalDateTime startDate);
    
    /**
     * Find cross-border transactions
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventCategory = 'FINANCIAL' AND a.metadata LIKE '%international%' ORDER BY a.timestamp DESC")
    List<AuditLog> findCrossBorderTransactions();
    
    // ========================================
    // DATA ACCESS AUDIT
    // ========================================
    
    /**
     * Find PII data access events
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventCategory = 'DATA_ACCESS' AND (a.gdprRelevant = true OR a.metadata LIKE '%PII%') ORDER BY a.timestamp DESC")
    List<AuditLog> findPiiDataAccessEvents();
    
    /**
     * Find data export events
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action LIKE '%export%' OR a.action LIKE '%download%' ORDER BY a.timestamp DESC")
    List<AuditLog> findDataExportEvents();
    
    /**
     * Find bulk data access events
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventCategory = 'DATA_ACCESS' AND a.metadata LIKE '%bulk%' ORDER BY a.timestamp DESC")
    List<AuditLog> findBulkDataAccessEvents();
    
    // ========================================
    // ADMINISTRATIVE AUDIT
    // ========================================
    
    /**
     * Find administrative configuration changes
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventCategory = 'CONFIGURATION' OR a.eventCategory = 'ADMIN' ORDER BY a.timestamp DESC")
    List<AuditLog> findAdministrativeChanges();
    
    /**
     * Find privilege escalation events
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action LIKE '%privilege%' OR a.action LIKE '%role%' OR a.action LIKE '%permission%' ORDER BY a.timestamp DESC")
    List<AuditLog> findPrivilegeEscalationEvents();
    
    // ========================================
    // RETENTION AND ARCHIVAL
    // ========================================
    
    /**
     * Find logs eligible for archival
     */
    @Query("SELECT a FROM AuditLog a WHERE a.archived = false AND a.retentionUntil <= :archivalDate")
    List<AuditLog> findLogsEligibleForArchival(@Param("archivalDate") LocalDateTime archivalDate);
    
    /**
     * Find archived logs eligible for deletion
     */
    @Query("SELECT a FROM AuditLog a WHERE a.archived = true AND a.retentionUntil <= :deletionDate")
    List<AuditLog> findArchivedLogsEligibleForDeletion(@Param("deletionDate") LocalDateTime deletionDate);
    
    /**
     * Count logs by retention policy
     */
    @Query("SELECT a.retentionPolicy, COUNT(a) FROM AuditLog a GROUP BY a.retentionPolicy")
    List<Object[]> countLogsByRetentionPolicy();
    
    // ========================================
    // INTEGRITY AND TAMPER DETECTION
    // ========================================
    
    /**
     * Find audit logs by sequence number range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.sequenceNumber >= :startSeq AND a.sequenceNumber <= :endSeq ORDER BY a.sequenceNumber ASC")
    List<AuditLog> findBySequenceNumberRange(@Param("startSeq") Long startSeq, @Param("endSeq") Long endSeq);
    
    /**
     * Find the last audit log by sequence number
     */
    Optional<AuditLog> findTopByOrderBySequenceNumberDesc();
    
    /**
     * Find gaps in sequence numbers (tamper detection)
     */
    @Query(value = "SELECT s.seq_num FROM generate_series((SELECT MIN(sequence_number) FROM audit_logs), (SELECT MAX(sequence_number) FROM audit_logs)) s(seq_num) WHERE NOT EXISTS (SELECT 1 FROM audit_logs WHERE sequence_number = s.seq_num)", nativeQuery = true)
    List<Long> findSequenceNumberGaps();
    
    // ========================================
    // COMPLIANCE REPORTING
    // ========================================
    
    /**
     * Get audit summary for compliance reporting
     */
    @Query("SELECT a.eventCategory, a.severity, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :startDate AND a.timestamp <= :endDate GROUP BY a.eventCategory, a.severity")
    List<Object[]> getAuditSummaryForPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get failed operations summary
     */
    @Query("SELECT a.eventType, COUNT(a) FROM AuditLog a WHERE a.result = 'FAILURE' AND a.timestamp >= :startDate GROUP BY a.eventType ORDER BY COUNT(a) DESC")
    List<Object[]> getFailedOperationsSummary(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Get user activity summary
     */
    @Query("SELECT a.userId, a.eventCategory, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :startDate GROUP BY a.userId, a.eventCategory HAVING COUNT(a) > :threshold")
    List<Object[]> getUserActivitySummary(@Param("startDate") LocalDateTime startDate, @Param("threshold") Long threshold);
    
    /**
     * Get compliance violation summary
     */
    @Query("SELECT DATE(a.timestamp), COUNT(a) FROM AuditLog a WHERE (a.result = 'FAILURE' OR a.severity IN ('HIGH', 'CRITICAL', 'EMERGENCY')) AND a.timestamp >= :startDate GROUP BY DATE(a.timestamp) ORDER BY DATE(a.timestamp)")
    List<Object[]> getComplianceViolationTrend(@Param("startDate") LocalDateTime startDate);
    
    // ========================================
    // ADVANCED SEARCH
    // ========================================
    
    /**
     * Complex search with multiple filters
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:eventCategory IS NULL OR a.eventCategory = :eventCategory) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:result IS NULL OR a.result = :result) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) AND " +
           "(:ipAddress IS NULL OR a.ipAddress = :ipAddress) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findWithFilters(@Param("userId") String userId,
                                  @Param("eventType") AuditEventType eventType,
                                  @Param("eventCategory") EventCategory eventCategory,
                                  @Param("severity") Severity severity,
                                  @Param("result") OperationResult result,
                                  @Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate,
                                  @Param("ipAddress") String ipAddress,
                                  Pageable pageable);
    
    /**
     * Search audit logs by text content
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "LOWER(a.description) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "LOWER(a.action) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "LOWER(a.metadata) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> searchByText(@Param("searchText") String searchText, Pageable pageable);
    
    // ========================================
    // STATISTICS AND METRICS
    // ========================================
    
    /**
     * Count total audit logs
     */
    @Query("SELECT COUNT(a) FROM AuditLog a")
    Long countTotalLogs();
    
    /**
     * Count logs by date range
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp >= :startDate AND a.timestamp <= :endDate")
    Long countLogsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get log volume by hour for the last 24 hours
     */
    @Query(value = "SELECT EXTRACT(HOUR FROM timestamp) as hour, COUNT(*) as count FROM audit_logs WHERE timestamp >= NOW() - INTERVAL '24 hours' GROUP BY EXTRACT(HOUR FROM timestamp) ORDER BY hour", nativeQuery = true)
    List<Object[]> getLogVolumeByHour();
    
    /**
     * Get top users by activity
     */
    @Query("SELECT a.userId, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :startDate GROUP BY a.userId ORDER BY COUNT(a) DESC")
    List<Object[]> getTopUsersByActivity(@Param("startDate") LocalDateTime startDate, Pageable pageable);
    
    // ========================================
    // ADDITIONAL SEARCH METHODS FOR AUDITSERARCHSERVICE
    // ========================================
    
    /**
     * Find by user ID and timestamp between
     */
    Page<AuditLog> findByUserIdAndTimestampUtcBetween(String userId, LocalDateTime startDate, 
                                                       LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find by user ID and timestamp after
     */
    Page<AuditLog> findByUserIdAndTimestampUtcAfter(String userId, LocalDateTime startDate, 
                                                     Pageable pageable);
    
    /**
     * Find by entity type and entity ID ordered by timestamp
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampAsc(String entityType, String entityId);
    
    /**
     * Find by event type and timestamp between
     */
    Page<AuditLog> findByEventTypeAndTimestampUtcBetween(AuditEventType eventType, 
                                                          LocalDateTime startDate,
                                                          LocalDateTime endDate, 
                                                          Pageable pageable);
    
    /**
     * Find by result and timestamp after
     */
    Page<AuditLog> findByResultAndTimestampUtcAfter(OperationResult result, 
                                                     LocalDateTime startDate, 
                                                     Pageable pageable);
    
    /**
     * Find suspicious activities
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestampUtc >= :startDate AND a.riskScore >= :riskThreshold ORDER BY a.riskScore DESC, a.timestampUtc DESC")
    List<AuditLog> findSuspiciousActivities(@Param("startDate") LocalDateTime startDate, 
                                            @Param("riskThreshold") Integer riskThreshold);
    
    /**
     * Find by investigation required
     */
    List<AuditLog> findByInvestigationRequiredTrueOrderByTimestampDesc();
    
    /**
     * Find PCI relevant logs by timestamp between
     */
    Page<AuditLog> findByPciRelevantTrueAndTimestampUtcBetween(LocalDateTime startDate, 
                                                                 LocalDateTime endDate, 
                                                                 Pageable pageable);
    
    /**
     * Find GDPR relevant logs by timestamp between
     */
    Page<AuditLog> findByGdprRelevantTrueAndTimestampUtcBetween(LocalDateTime startDate, 
                                                                  LocalDateTime endDate, 
                                                                  Pageable pageable);
    
    /**
     * Find SOX relevant logs by timestamp between
     */
    Page<AuditLog> findBySoxRelevantTrueAndTimestampUtcBetween(LocalDateTime startDate, 
                                                                 LocalDateTime endDate, 
                                                                 Pageable pageable);
    
    /**
     * Find by timestamp between (no pagination)
     */
    List<AuditLog> findByTimestampUtcBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find by IP address and timestamp after
     */
    Page<AuditLog> findByIpAddressAndTimestampUtcAfter(String ipAddress, 
                                                        LocalDateTime startDate, 
                                                        Pageable pageable);
    
    /**
     * Find by IP address ordered by timestamp
     */
    Page<AuditLog> findByIpAddressOrderByTimestampDesc(String ipAddress, Pageable pageable);
    
    /**
     * Count by timestamp after
     */
    Long countByTimestampUtcAfter(LocalDateTime startDate);
    
    /**
     * Find by timestamp after
     */
    List<AuditLog> findByTimestampUtcAfter(LocalDateTime startDate);
    
    /**
     * Find by sequence number between ordered by sequence number
     */
    List<AuditLog> findBySequenceNumberBetweenOrderBySequenceNumberAsc(Long startSeq, Long endSeq);
}