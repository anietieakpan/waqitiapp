package com.waqiti.common.audit;

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
 * Repository for audit log entities
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    /**
     * Find audit logs by user ID
     */
    List<AuditLogEntity> findByUserIdOrderByTimestampDesc(String userId);

    /**
     * Find audit logs by transaction ID
     */
    List<AuditLogEntity> findByTransactionIdOrderByTimestampDesc(String transactionId);

    /**
     * Find audit logs by event type
     */
    List<AuditLogEntity> findByEventTypeOrderByTimestampDesc(String eventType);

    /**
     * Find audit logs within time range
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AuditLogEntity> findByTimestampBetween(@Param("startTime") LocalDateTime startTime, 
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * Find audit logs by severity level
     */
    List<AuditLogEntity> findBySeverityOrderByTimestampDesc(String severity);

    /**
     * Count audit logs by event type within time range
     */
    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.eventType = :eventType AND a.timestamp BETWEEN :startTime AND :endTime")
    long countByEventTypeAndTimestampBetween(@Param("eventType") String eventType,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);
    
    /**
     * Save security log
     */
    default SecurityAuditEntity saveSecurityLog(SecurityAuditEntity entity) {
        AuditLogEntity auditLog = AuditLogEntity.builder()
            .id(entity.getId())
            .userId(entity.getUserId())
            .type(AuditType.SECURITY)
            .action(entity.getEventType())
            .status(entity.getSeverity() != null ? entity.getSeverity().name() : "INFO")
            .ipAddress(entity.getIpAddress())
            .description(entity.getDetails())
            .timestamp(entity.getTimestamp())
            .build();
        save(auditLog);
        return entity;
    }
    
    /**
     * Save data access log
     */
    default DataAccessEntity saveDataAccessLog(DataAccessEntity entity) {
        AuditLogEntity auditLog = AuditLogEntity.builder()
            .id(entity.getId())
            .userId(entity.getAccessedBy())
            .type(AuditType.DATA_ACCESS)
            .action(entity.getDataType())
            .timestamp(entity.getTimestamp())
            .build();
        save(auditLog);
        return entity;
    }
    
    /**
     * Save compliance log
     */
    default ComplianceEntity saveComplianceLog(ComplianceEntity entity) {
        AuditLogEntity auditLog = AuditLogEntity.builder()
            .id(entity.getId())
            .userId(entity.getUserId())
            .type(AuditType.COMPLIANCE)
            .action(entity.getEventType())
            .description(entity.getDescription() != null ? entity.getDescription() : "")
            .timestamp(entity.getTimestamp())
            .build();
        save(auditLog);
        return entity;
    }
    
    /**
     * Search audit logs with criteria
     */
    default Page<AuditLogEntity> searchAuditLogs(AuditSearchCriteria criteria, Pageable pageable) {
        // This would need a custom implementation with Specification or QueryDSL
        // For now, returning empty page
        return Page.empty(pageable);
    }
    
    /**
     * Find by date range and type
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE a.timestamp BETWEEN :startDate AND :endDate AND (:type IS NULL OR a.type = :type) ORDER BY a.timestamp DESC")
    List<AuditLogEntity> findByDateRangeAndType(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate,
                                               @Param("type") AuditType type);
    
    /**
     * Find by transaction ID
     */
    default List<AuditLogEntity> findByTransactionId(String transactionId) {
        return findByTransactionIdOrderByTimestampDesc(transactionId);
    }
}