package com.waqiti.audit.repository;

import com.waqiti.audit.domain.AuditLog;
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
 * Repository for audit log persistence and queries
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    
    /**
     * Find audit logs by user ID
     */
    Page<AuditLog> findByUserId(String userId, Pageable pageable);
    
    /**
     * Find audit logs by entity
     */
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId);
    
    /**
     * Find audit logs by time range
     */
    List<AuditLog> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find audit logs by event type and time range
     */
    List<AuditLog> findByEventTypeAndTimestampBetween(String eventType, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find audit logs by correlation ID
     */
    List<AuditLog> findByCorrelationId(String correlationId);
    
    /**
     * Find high-risk audit events
     */
    @Query("SELECT a FROM AuditLog a WHERE a.riskLevel IN ('HIGH', 'CRITICAL') AND a.timestamp >= :startDate")
    List<AuditLog> findHighRiskEvents(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Find audit logs by service origin
     */
    List<AuditLog> findByServiceOriginAndTimestampBetween(String serviceOrigin, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Count events by type for a time period
     */
    @Query("SELECT a.eventType, COUNT(a) FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate GROUP BY a.eventType")
    List<Object[]> countEventsByType(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find suspicious patterns
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.timestamp >= :startDate " +
           "GROUP BY a.sourceIpAddress HAVING COUNT(DISTINCT a.sourceIpAddress) > :threshold")
    List<AuditLog> findSuspiciousLoginPatterns(@Param("userId") String userId, 
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("threshold") Long threshold);
    
    /**
     * Find audit logs for compliance reporting
     */
    @Query("SELECT a FROM AuditLog a WHERE :complianceFlag MEMBER OF a.complianceFlags AND a.timestamp BETWEEN :startDate AND :endDate")
    List<AuditLog> findByComplianceFlag(@Param("complianceFlag") String complianceFlag,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find unarchived logs older than retention period
     */
    @Query("SELECT a FROM AuditLog a WHERE a.isArchived = false AND a.timestamp < :cutoffDate")
    List<AuditLog> findLogsForArchival(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Check integrity hash uniqueness
     */
    boolean existsByIntegrityHash(String integrityHash);
}