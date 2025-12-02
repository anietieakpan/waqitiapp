package com.waqiti.payment.repository;

import com.waqiti.payment.domain.ScheduledPaymentExecution;
import com.waqiti.payment.domain.ScheduledPaymentExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Scheduled Payment Execution Repository - PRODUCTION READY
 *
 * Data access layer for scheduled payment execution records
 * Provides comprehensive querying capabilities with indexes
 */
@Repository
public interface ScheduledPaymentExecutionRepository extends JpaRepository<ScheduledPaymentExecution, UUID> {

    /**
     * Find executions by scheduled payment ID
     */
    List<ScheduledPaymentExecution> findByScheduledPaymentIdOrderByExecutionDateDesc(UUID scheduledPaymentId);

    /**
     * Find executions by scheduled payment ID with pagination
     */
    Page<ScheduledPaymentExecution> findByScheduledPaymentId(UUID scheduledPaymentId, Pageable pageable);

    /**
     * Find executions by status
     */
    Page<ScheduledPaymentExecution> findByStatus(ScheduledPaymentExecutionStatus status, Pageable pageable);

    /**
     * Find execution by transaction ID
     */
    Optional<ScheduledPaymentExecution> findByTransactionId(UUID transactionId);

    /**
     * Find execution by idempotency key
     */
    Optional<ScheduledPaymentExecution> findByIdempotencyKey(String idempotencyKey);

    /**
     * Count executions by status for a scheduled payment
     */
    long countByScheduledPaymentIdAndStatus(UUID scheduledPaymentId, ScheduledPaymentExecutionStatus status);

    /**
     * Find failed executions that can be retried
     */
    @Query("SELECT spe FROM ScheduledPaymentExecution spe " +
           "WHERE spe.status = 'FAILED' " +
           "AND (spe.retryCount IS NULL OR spe.retryCount < 3) " +
           "AND spe.executionDate >= :since " +
           "ORDER BY spe.executionDate DESC")
    List<ScheduledPaymentExecution> findRetryableFailures(@Param("since") LocalDateTime since);

    /**
     * Find recent executions for monitoring
     */
    @Query("SELECT spe FROM ScheduledPaymentExecution spe " +
           "WHERE spe.executionDate >= :since " +
           "ORDER BY spe.executionDate DESC")
    List<ScheduledPaymentExecution> findRecentExecutions(@Param("since") LocalDateTime since);

    /**
     * Get execution success rate for a scheduled payment
     */
    @Query("SELECT CAST(COUNT(CASE WHEN spe.status = 'COMPLETED' THEN 1 END) AS double) / COUNT(*) " +
           "FROM ScheduledPaymentExecution spe " +
           "WHERE spe.scheduledPayment.id = :scheduledPaymentId")
    Double getSuccessRate(@Param("scheduledPaymentId") UUID scheduledPaymentId);

    /**
     * Get average processing duration for successful executions
     */
    @Query("SELECT AVG(spe.processingDurationMs) FROM ScheduledPaymentExecution spe " +
           "WHERE spe.status = 'COMPLETED' " +
           "AND spe.processingDurationMs IS NOT NULL " +
           "AND spe.executionDate >= :since")
    Double getAverageProcessingDuration(@Param("since") LocalDateTime since);

    /**
     * Find executions with long processing time (potential issues)
     */
    @Query("SELECT spe FROM ScheduledPaymentExecution spe " +
           "WHERE spe.processingDurationMs > :thresholdMs " +
           "AND spe.executionDate >= :since " +
           "ORDER BY spe.processingDurationMs DESC")
    List<ScheduledPaymentExecution> findSlowExecutions(
            @Param("thresholdMs") Long thresholdMs,
            @Param("since") LocalDateTime since
    );

    /**
     * Get execution statistics for a scheduled payment
     */
    @Query("SELECT new map(" +
           "COUNT(*) as totalExecutions, " +
           "SUM(CASE WHEN spe.status = 'COMPLETED' THEN 1 ELSE 0 END) as successfulExecutions, " +
           "SUM(CASE WHEN spe.status = 'FAILED' THEN 1 ELSE 0 END) as failedExecutions, " +
           "AVG(spe.processingDurationMs) as avgProcessingTimeMs" +
           ") FROM ScheduledPaymentExecution spe " +
           "WHERE spe.scheduledPayment.id = :scheduledPaymentId")
    Object getExecutionStatistics(@Param("scheduledPaymentId") UUID scheduledPaymentId);

    /**
     * Find last successful execution for a scheduled payment
     */
    @Query("SELECT spe FROM ScheduledPaymentExecution spe " +
           "WHERE spe.scheduledPayment.id = :scheduledPaymentId " +
           "AND spe.status = 'COMPLETED' " +
           "ORDER BY spe.executionDate DESC")
    Page<ScheduledPaymentExecution> findLastSuccessfulExecution(
            @Param("scheduledPaymentId") UUID scheduledPaymentId,
            Pageable pageable
    );

    /**
     * Find executions by date range
     */
    @Query("SELECT spe FROM ScheduledPaymentExecution spe " +
           "WHERE spe.executionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY spe.executionDate DESC")
    Page<ScheduledPaymentExecution> findByExecutionDateBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Find executions by error code (for debugging)
     */
    List<ScheduledPaymentExecution> findByErrorCodeOrderByExecutionDateDesc(String errorCode);

    /**
     * Delete old execution records (cleanup)
     */
    @Query("DELETE FROM ScheduledPaymentExecution spe " +
           "WHERE spe.executionDate < :cutoffDate " +
           "AND spe.status IN ('COMPLETED', 'FAILED')")
    void deleteOldExecutions(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Check if execution exists with idempotency key
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
}
