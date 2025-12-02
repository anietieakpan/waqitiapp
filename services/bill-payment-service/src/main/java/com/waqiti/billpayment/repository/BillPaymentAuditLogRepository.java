package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.BillPaymentAuditLog;
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
 * Repository for BillPaymentAuditLog entity operations
 */
@Repository
public interface BillPaymentAuditLogRepository extends JpaRepository<BillPaymentAuditLog, UUID> {

    List<BillPaymentAuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    Page<BillPaymentAuditLog> findByUserId(String userId, Pageable pageable);

    List<BillPaymentAuditLog> findByUserIdAndAction(String userId, String action);

    @Query("SELECT bal FROM BillPaymentAuditLog bal WHERE bal.entityType = :entityType " +
           "AND bal.entityId = :entityId ORDER BY bal.timestamp DESC")
    List<BillPaymentAuditLog> findAuditTrail(@Param("entityType") String entityType,
                                              @Param("entityId") UUID entityId);

    @Query("SELECT bal FROM BillPaymentAuditLog bal WHERE bal.timestamp BETWEEN :startDate AND :endDate")
    List<BillPaymentAuditLog> findByTimestampBetween(@Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);

    @Query("SELECT bal FROM BillPaymentAuditLog bal WHERE bal.correlationId = :correlationId " +
           "ORDER BY bal.timestamp")
    List<BillPaymentAuditLog> findByCorrelationId(@Param("correlationId") String correlationId);

    long countByUserIdAndTimestampAfter(String userId, LocalDateTime timestamp);
}
