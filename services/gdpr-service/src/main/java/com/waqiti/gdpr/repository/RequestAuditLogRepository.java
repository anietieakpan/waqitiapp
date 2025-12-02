package com.waqiti.gdpr.repository;

import com.waqiti.gdpr.domain.RequestAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Request Audit Logs
 * Production-ready Spring Data JPA repository for GDPR audit trail
 */
@Repository
public interface RequestAuditLogRepository extends JpaRepository<RequestAuditLog, String> {

    /**
     * Find audit logs by request ID
     */
    @Query("SELECT a FROM RequestAuditLog a WHERE a.request.id = :requestId ORDER BY a.performedAt DESC")
    List<RequestAuditLog> findByRequestId(@Param("requestId") String requestId);

    /**
     * Find audit logs by action
     */
    List<RequestAuditLog> findByAction(String action);

    /**
     * Find audit logs performed by a specific user
     */
    List<RequestAuditLog> findByPerformedBy(String performedBy);

    /**
     * Find audit logs within a date range
     */
    @Query("SELECT a FROM RequestAuditLog a " +
           "WHERE a.performedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY a.performedAt DESC")
    List<RequestAuditLog> findByPerformedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find audit logs by request ID and action
     */
    @Query("SELECT a FROM RequestAuditLog a " +
           "WHERE a.request.id = :requestId " +
           "AND a.action = :action " +
           "ORDER BY a.performedAt DESC")
    List<RequestAuditLog> findByRequestIdAndAction(
            @Param("requestId") String requestId,
            @Param("action") String action
    );

    /**
     * Count audit logs by action
     */
    long countByAction(String action);
}
