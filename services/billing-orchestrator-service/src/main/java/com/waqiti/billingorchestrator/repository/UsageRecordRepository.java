package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.UsageRecord;
import com.waqiti.billingorchestrator.entity.UsageRecord.MetricCategory;
import com.waqiti.billingorchestrator.entity.UsageRecord.UsageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UsageRecord entities
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    /**
     * Find usage records by account
     */
    Page<UsageRecord> findByAccountId(UUID accountId, Pageable pageable);

    /**
     * Find usage records by idempotency key (prevents duplicates)
     */
    Optional<UsageRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Check if usage record exists by idempotency key
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Find usage records for a billing period
     */
    @Query("SELECT u FROM UsageRecord u WHERE u.accountId = :accountId " +
           "AND u.usageTimestamp BETWEEN :startDate AND :endDate")
    List<UsageRecord> findByAccountAndPeriod(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find unbilled usage records for account
     */
    @Query("SELECT u FROM UsageRecord u WHERE u.accountId = :accountId AND u.billed = FALSE")
    List<UsageRecord> findUnbilledUsage(@Param("accountId") UUID accountId);

    /**
     * Aggregate usage by metric for billing period
     */
    @Query("SELECT u.metricName AS metric, SUM(u.quantity) AS totalQuantity, " +
           "SUM(u.totalAmount) AS totalAmount FROM UsageRecord u " +
           "WHERE u.accountId = :accountId " +
           "AND u.usageTimestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY u.metricName")
    List<Map<String, Object>> aggregateUsageByMetric(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get total usage amount for billing period (specific account)
     */
    @Query("SELECT COALESCE(SUM(u.totalAmount), 0) FROM UsageRecord u " +
           "WHERE u.accountId = :accountId " +
           "AND u.usageTimestamp BETWEEN :startDate AND :endDate")
    BigDecimal getTotalUsageAmount(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get total usage amount for billing period (all accounts)
     * Used for revenue analytics aggregation
     */
    @Query("SELECT COALESCE(SUM(u.totalAmount), 0) FROM UsageRecord u " +
           "WHERE u.usageTimestamp BETWEEN :startDate AND :endDate")
    BigDecimal getTotalUsageAmountByPeriod(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count usage records by status
     */
    long countByAccountIdAndStatus(UUID accountId, UsageStatus status);

    /**
     * Find usage by metric and period
     */
    List<UsageRecord> findByAccountIdAndMetricNameAndUsageTimestampBetween(
        UUID accountId,
        String metricName,
        LocalDateTime start,
        LocalDateTime end
    );

    /**
     * Mark usage records as billed
     */
    @Query("UPDATE UsageRecord u SET u.billed = TRUE, u.billedAt = :billedAt, " +
           "u.invoiceId = :invoiceId, u.status = 'BILLED' " +
           "WHERE u.id IN :usageIds")
    void markAsBilled(
        @Param("usageIds") List<UUID> usageIds,
        @Param("invoiceId") UUID invoiceId,
        @Param("billedAt") LocalDateTime billedAt
    );

    /**
     * Find top consumers by metric
     */
    @Query("SELECT u.accountId, SUM(u.quantity) AS total FROM UsageRecord u " +
           "WHERE u.metricName = :metricName " +
           "AND u.usageTimestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY u.accountId ORDER BY total DESC")
    List<Map<String, Object>> findTopConsumers(
        @Param("metricName") String metricName,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
}
