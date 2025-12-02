package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Audit Trail Repository
 * Repository for audit log entries
 */
@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, UUID> {

    /**
     * Find audit logs for an entity
     */
    List<AuditTrail> findByEntityTypeAndEntityIdOrderByTimestampDesc(
        String entityType, UUID entityId);

    /**
     * Find audit logs by user
     */
    List<AuditTrail> findByUserIdOrderByTimestampDesc(String userId);

    /**
     * Find audit logs by action
     */
    List<AuditTrail> findByAction(String action);

    /**
     * Find audit logs within date range
     */
    @Query("SELECT at FROM AuditTrail at WHERE at.timestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY at.timestamp DESC")
    List<AuditTrail> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Find audit logs for entity type
     */
    List<AuditTrail> findByEntityTypeOrderByTimestampDesc(String entityType);

    /**
     * Find recent audit logs with limit
     */
    @Query("SELECT at FROM AuditTrail at ORDER BY at.timestamp DESC")
    List<AuditTrail> findRecentLogs(@Param("limit") int limit);

    /**
     * Count logs by user in date range
     */
    @Query("SELECT COUNT(at) FROM AuditTrail at WHERE at.userId = :userId " +
           "AND at.timestamp BETWEEN :startDate AND :endDate")
    long countByUserAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Find logs by IP address
     */
    List<AuditTrail> findByIpAddressOrderByTimestampDesc(String ipAddress);
}
