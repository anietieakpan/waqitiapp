package com.waqiti.webhook.repository;

import com.waqiti.webhook.entity.WebhookDelivery;
import com.waqiti.webhook.entity.WebhookEnums.WebhookStatus;
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

/**
 * Repository for WebhookDelivery entity
 *
 * Tracks individual delivery attempts for webhooks with detailed metadata,
 * response codes, and timing information for monitoring and debugging.
 *
 * PRODUCTION-GRADE FEATURES:
 * - Delivery history tracking
 * - Performance analytics
 * - Error pattern detection
 * - Response time monitoring
 * - Success rate calculations
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-20
 */
@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {

    // ========== CORE QUERIES ==========

    /**
     * Find all deliveries for a subscription
     */
    Page<WebhookDelivery> findBySubscriptionIdOrderByDeliveredAtDesc(
            String subscriptionId, Pageable pageable);

    /**
     * Find deliveries by subscription and status
     */
    Page<WebhookDelivery> findBySubscriptionIdAndStatusOrderByDeliveredAtDesc(
            String subscriptionId, WebhookStatus status, Pageable pageable);

    /**
     * Find deliveries by event type
     */
    Page<WebhookDelivery> findByEventTypeOrderByDeliveredAtDesc(
            String eventType, Pageable pageable);

    /**
     * Find deliveries by subscription and event type
     */
    Page<WebhookDelivery> findBySubscriptionIdAndEventTypeOrderByDeliveredAtDesc(
            String subscriptionId, String eventType, Pageable pageable);

    // ========== TIME-RANGE QUERIES ==========

    /**
     * Find deliveries within time range
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.deliveredAt BETWEEN :startTime AND :endTime " +
           "ORDER BY d.deliveredAt DESC")
    Page<WebhookDelivery> findByDeliveredAtBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * Find deliveries for subscription within time range
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.deliveredAt BETWEEN :startTime AND :endTime " +
           "ORDER BY d.deliveredAt DESC")
    Page<WebhookDelivery> findBySubscriptionIdAndTimeRange(
            @Param("subscriptionId") String subscriptionId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    // ========== STATISTICS QUERIES ==========

    /**
     * Count deliveries by status for subscription
     */
    @Query("SELECT COUNT(d) FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.status = :status")
    long countBySubscriptionIdAndStatus(@Param("subscriptionId") String subscriptionId,
                                         @Param("status") WebhookStatus status);

    /**
     * Count total deliveries for subscription
     */
    long countBySubscriptionId(String subscriptionId);

    /**
     * Get success rate for subscription
     */
    @Query("SELECT CAST(COUNT(CASE WHEN d.status = 'DELIVERED' THEN 1 END) AS double) / " +
           "CAST(COUNT(d) AS double) * 100.0 " +
           "FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId")
    Double getSuccessRateBySubscriptionId(@Param("subscriptionId") String subscriptionId);

    /**
     * Get average response time for subscription (successful deliveries only)
     */
    @Query("SELECT AVG(d.responseTimeMs) FROM WebhookDelivery d " +
           "WHERE d.subscriptionId = :subscriptionId " +
           "AND d.status = 'DELIVERED' " +
           "AND d.responseTimeMs IS NOT NULL")
    Double getAverageResponseTime(@Param("subscriptionId") String subscriptionId);

    /**
     * Get p95 response time for subscription
     */
    @Query(value = "SELECT PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY response_time_ms) " +
                   "FROM webhook_deliveries " +
                   "WHERE subscription_id = :subscriptionId " +
                   "AND status = 'DELIVERED' " +
                   "AND response_time_ms IS NOT NULL",
           nativeQuery = true)
    Double getP95ResponseTime(@Param("subscriptionId") String subscriptionId);

    // ========== FAILURE ANALYSIS ==========

    /**
     * Find failed deliveries for analysis
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.status = 'FAILED' " +
           "ORDER BY d.deliveredAt DESC")
    Page<WebhookDelivery> findFailedDeliveries(@Param("subscriptionId") String subscriptionId,
                                                Pageable pageable);

    /**
     * Find deliveries with specific HTTP status code
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.responseCode = :responseCode " +
           "ORDER BY d.deliveredAt DESC")
    Page<WebhookDelivery> findByResponseCode(@Param("subscriptionId") String subscriptionId,
                                              @Param("responseCode") int responseCode,
                                              Pageable pageable);

    /**
     * Find deliveries with response codes in range (e.g., 5xx errors)
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.responseCode BETWEEN :minCode AND :maxCode " +
           "ORDER BY d.deliveredAt DESC")
    Page<WebhookDelivery> findByResponseCodeRange(@Param("subscriptionId") String subscriptionId,
                                                    @Param("minCode") int minCode,
                                                    @Param("maxCode") int maxCode,
                                                    Pageable pageable);

    /**
     * Count failures by HTTP status code
     */
    @Query("SELECT d.responseCode, COUNT(d) FROM WebhookDelivery d " +
           "WHERE d.subscriptionId = :subscriptionId " +
           "AND d.status = 'FAILED' " +
           "GROUP BY d.responseCode " +
           "ORDER BY COUNT(d) DESC")
    List<Object[]> countFailuresByResponseCode(@Param("subscriptionId") String subscriptionId);

    // ========== RETRY ANALYSIS ==========

    /**
     * Find deliveries with high retry attempts
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.attempts >= :threshold " +
           "ORDER BY d.attempts DESC, d.deliveredAt DESC")
    List<WebhookDelivery> findByHighRetryAttempts(@Param("subscriptionId") String subscriptionId,
                                                    @Param("threshold") int threshold,
                                                    Pageable pageable);

    /**
     * Get average retry attempts for failed deliveries
     */
    @Query("SELECT AVG(d.attempts) FROM WebhookDelivery d " +
           "WHERE d.subscriptionId = :subscriptionId " +
           "AND d.status = 'FAILED'")
    Double getAverageRetryAttempts(@Param("subscriptionId") String subscriptionId);

    // ========== RECENT ACTIVITY ==========

    /**
     * Find most recent delivery for subscription
     */
    Optional<WebhookDelivery> findFirstBySubscriptionIdOrderByDeliveredAtDesc(
            String subscriptionId);

    /**
     * Find most recent successful delivery
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.status = 'DELIVERED' " +
           "ORDER BY d.deliveredAt DESC")
    Optional<WebhookDelivery> findMostRecentSuccessfulDelivery(
            @Param("subscriptionId") String subscriptionId,
            Pageable pageable);

    /**
     * Find recent deliveries (last N hours)
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.deliveredAt >= :since " +
           "ORDER BY d.deliveredAt DESC")
    List<WebhookDelivery> findRecentDeliveries(@Param("subscriptionId") String subscriptionId,
                                                @Param("since") LocalDateTime since);

    // ========== EVENT-SPECIFIC QUERIES ==========

    /**
     * Count deliveries by event type for subscription
     */
    @Query("SELECT d.eventType, COUNT(d) FROM WebhookDelivery d " +
           "WHERE d.subscriptionId = :subscriptionId " +
           "GROUP BY d.eventType " +
           "ORDER BY COUNT(d) DESC")
    List<Object[]> countByEventType(@Param("subscriptionId") String subscriptionId);

    /**
     * Get success rate by event type
     */
    @Query("SELECT d.eventType, " +
           "CAST(COUNT(CASE WHEN d.status = 'DELIVERED' THEN 1 END) AS double) / " +
           "CAST(COUNT(d) AS double) * 100.0 as successRate " +
           "FROM WebhookDelivery d " +
           "WHERE d.subscriptionId = :subscriptionId " +
           "GROUP BY d.eventType " +
           "ORDER BY successRate ASC")
    List<Object[]> getSuccessRateByEventType(@Param("subscriptionId") String subscriptionId);

    // ========== MONITORING & ALERTING ==========

    /**
     * Find consecutive failures (potential endpoint issues)
     */
    @Query(value = "SELECT * FROM webhook_deliveries d " +
                   "WHERE d.subscription_id = :subscriptionId " +
                   "AND d.status = 'FAILED' " +
                   "ORDER BY d.delivered_at DESC " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<WebhookDelivery> findConsecutiveFailures(@Param("subscriptionId") String subscriptionId,
                                                    @Param("limit") int limit);

    /**
     * Count consecutive failures since last success
     */
    @Query(value = "SELECT COUNT(*) FROM webhook_deliveries d1 " +
                   "WHERE d1.subscription_id = :subscriptionId " +
                   "AND d1.status = 'FAILED' " +
                   "AND d1.delivered_at > COALESCE(" +
                   "  (SELECT MAX(d2.delivered_at) FROM webhook_deliveries d2 " +
                   "   WHERE d2.subscription_id = :subscriptionId " +
                   "   AND d2.status = 'DELIVERED'), " +
                   "  '1970-01-01')",
           nativeQuery = true)
    long countConsecutiveFailuresSinceLastSuccess(@Param("subscriptionId") String subscriptionId);

    /**
     * Find slow deliveries (above threshold)
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subscriptionId " +
           "AND d.responseTimeMs > :thresholdMs " +
           "ORDER BY d.responseTimeMs DESC")
    List<WebhookDelivery> findSlowDeliveries(@Param("subscriptionId") String subscriptionId,
                                              @Param("thresholdMs") long thresholdMs,
                                              Pageable pageable);

    // ========== CLEANUP ==========

    /**
     * Delete old deliveries (data retention)
     */
    @Modifying
    @Query("DELETE FROM WebhookDelivery d WHERE d.deliveredAt < :cutoffDate")
    int deleteOldDeliveries(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Delete old successful deliveries (keep failures longer for debugging)
     */
    @Modifying
    @Query("DELETE FROM WebhookDelivery d WHERE d.status = 'DELIVERED' " +
           "AND d.deliveredAt < :cutoffDate")
    int deleteOldSuccessfulDeliveries(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== AGGREGATIONS ==========

    /**
     * Get delivery statistics for time period
     */
    @Query("SELECT " +
           "COUNT(d) as total, " +
           "SUM(CASE WHEN d.status = 'DELIVERED' THEN 1 ELSE 0 END) as successful, " +
           "SUM(CASE WHEN d.status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
           "AVG(CASE WHEN d.status = 'DELIVERED' THEN d.responseTimeMs END) as avgResponseTime, " +
           "MIN(CASE WHEN d.status = 'DELIVERED' THEN d.responseTimeMs END) as minResponseTime, " +
           "MAX(CASE WHEN d.status = 'DELIVERED' THEN d.responseTimeMs END) as maxResponseTime " +
           "FROM WebhookDelivery d " +
           "WHERE d.subscriptionId = :subscriptionId " +
           "AND d.deliveredAt BETWEEN :startTime AND :endTime")
    Object[] getDeliveryStatistics(@Param("subscriptionId") String subscriptionId,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    /**
     * Get hourly delivery counts for graphing
     */
    @Query(value = "SELECT DATE_TRUNC('hour', delivered_at) as hour, " +
                   "COUNT(*) as count, " +
                   "SUM(CASE WHEN status = 'DELIVERED' THEN 1 ELSE 0 END) as successful " +
                   "FROM webhook_deliveries " +
                   "WHERE subscription_id = :subscriptionId " +
                   "AND delivered_at BETWEEN :startTime AND :endTime " +
                   "GROUP BY DATE_TRUNC('hour', delivered_at) " +
                   "ORDER BY hour",
           nativeQuery = true)
    List<Object[]> getHourlyDeliveryCounts(@Param("subscriptionId") String subscriptionId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);
}
