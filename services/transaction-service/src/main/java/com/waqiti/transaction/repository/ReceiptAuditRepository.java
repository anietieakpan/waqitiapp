package com.waqiti.transaction.repository;

import com.waqiti.transaction.entity.ReceiptAuditLogEntity;
import com.waqiti.transaction.enums.ReceiptAuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Receipt audit logs
 */
@Repository
public interface ReceiptAuditRepository extends JpaRepository<ReceiptAuditLogEntity, UUID> {

    /**
     * Find audit logs by transaction ID ordered by timestamp
     */
    List<ReceiptAuditLogEntity> findByTransactionIdOrderByTimestampDesc(UUID transactionId);

    /**
     * Find audit logs by user ID with limit
     */
    @Query("SELECT a FROM ReceiptAuditLogEntity a WHERE a.userId = :userId ORDER BY a.timestamp DESC")
    List<ReceiptAuditLogEntity> findByUserIdOrderByTimestampDesc(@Param("userId") String userId, int limit);

    /**
     * Find suspicious activities since a certain time
     */
    @Query("SELECT a FROM ReceiptAuditLogEntity a WHERE a.action = 'SUSPICIOUS_ACTIVITY' AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<ReceiptAuditLogEntity> findSuspiciousActivitiesSince(@Param("since") LocalDateTime since);

    /**
     * Find logs between timestamps
     */
    List<ReceiptAuditLogEntity> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Count receipts by user since a certain time
     */
    @Query("SELECT a.userId, COUNT(a) FROM ReceiptAuditLogEntity a WHERE a.action = 'RECEIPT_GENERATED' AND a.timestamp >= :since GROUP BY a.userId")
    List<Object[]> countReceiptsByUserSince(@Param("since") LocalDateTime since);

    /**
     * Find old logs for archival
     */
    @Query("SELECT a FROM ReceiptAuditLogEntity a WHERE a.timestamp < :cutoff")
    List<ReceiptAuditLogEntity> findOldLogs(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Delete old logs
     */
    @Modifying
    @Query("DELETE FROM ReceiptAuditLogEntity a WHERE a.timestamp < :cutoff")
    void deleteOldLogs(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Find flagged logs for review
     */
    List<ReceiptAuditLogEntity> findByFlaggedForReviewTrueOrderByTimestampDesc();

    /**
     * Count logs by action type
     */
    @Query("SELECT a.action, COUNT(a) FROM ReceiptAuditLogEntity a GROUP BY a.action")
    List<Object[]> countByAction();

    /**
     * Find high risk activities
     */
    List<ReceiptAuditLogEntity> findByRiskLevelOrderByTimestampDesc(String riskLevel);

    /**
     * Find logs by receipt ID
     */
    List<ReceiptAuditLogEntity> findByReceiptIdOrderByTimestampDesc(UUID receiptId);

    /**
     * Count total logs
     */
    @Query("SELECT COUNT(a) FROM ReceiptAuditLogEntity a")
    long countTotalLogs();

    /**
     * Get audit statistics
     */
    @Query("SELECT a.action, COUNT(a), AVG(a.securityScore) FROM ReceiptAuditLogEntity a WHERE a.timestamp >= :since GROUP BY a.action")
    List<Object[]> getAuditStatistics(@Param("since") LocalDateTime since);

    /**
     * Find logs by client IP
     */
    List<ReceiptAuditLogEntity> findByClientIpOrderByTimestampDesc(String clientIp);

    /**
     * Find recent failed operations
     */
    @Query("SELECT a FROM ReceiptAuditLogEntity a WHERE a.success = false AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<ReceiptAuditLogEntity> findRecentFailedOperations(@Param("since") LocalDateTime since);
}