package com.waqiti.webhook.repository;

import com.waqiti.webhook.entity.Webhook;
import com.waqiti.webhook.entity.WebhookEnums.WebhookStatus;
import com.waqiti.webhook.entity.WebhookEnums.WebhookPriority;
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
 * Repository for Webhook entity
 *
 * PRODUCTION-GRADE FEATURES:
 * - Comprehensive query methods for all webhook statuses
 * - Retry queue management
 * - Dead letter queue support
 * - Performance-optimized queries with indexes
 * - Bulk operations support
 * - Metrics and monitoring queries
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-20
 */
@Repository
public interface WebhookRepository extends JpaRepository<Webhook, String> {

    // ========== CORE QUERIES ==========

    /**
     * Find webhook by event ID for idempotency checks
     */
    Optional<Webhook> findByEventId(String eventId);

    /**
     * Find webhooks by subscription ID
     */
    Page<Webhook> findBySubscriptionId(String subscriptionId, Pageable pageable);

    /**
     * Find webhooks by subscription ID and status
     */
    Page<Webhook> findBySubscriptionIdAndStatus(String subscriptionId,
                                                  WebhookStatus status,
                                                  Pageable pageable);

    /**
     * Find webhooks by tenant ID
     */
    Page<Webhook> findByTenantId(String tenantId, Pageable pageable);

    // ========== STATUS-BASED QUERIES ==========

    /**
     * Find all pending webhooks (for initial delivery)
     */
    @Query("SELECT w FROM Webhook w WHERE w.status = 'PENDING' ORDER BY w.priority DESC, w.createdAt ASC")
    List<Webhook> findPendingWebhooks(Pageable pageable);

    /**
     * Find webhooks ready for retry
     * Critical for retry scheduler - checks next_retry_at timestamp
     */
    @Query("SELECT w FROM Webhook w WHERE w.status = 'PENDING_RETRY' " +
           "AND w.nextRetryAt <= :now " +
           "AND w.attemptCount < :maxAttempts " +
           "ORDER BY w.priority DESC, w.nextRetryAt ASC")
    List<Webhook> findWebhooksReadyForRetry(@Param("now") LocalDateTime now,
                                             @Param("maxAttempts") int maxAttempts,
                                             Pageable pageable);

    /**
     * Find failed webhooks for dead letter queue
     */
    @Query("SELECT w FROM Webhook w WHERE w.status = 'FAILED' " +
           "AND w.failedAt IS NOT NULL " +
           "ORDER BY w.failedAt DESC")
    List<Webhook> findFailedWebhooks(Pageable pageable);

    /**
     * Find expired webhooks (old pending/retry webhooks)
     */
    @Query("SELECT w FROM Webhook w WHERE w.status IN ('PENDING', 'PENDING_RETRY') " +
           "AND w.createdAt < :cutoffTime")
    List<Webhook> findExpiredWebhooks(@Param("cutoffTime") LocalDateTime cutoffTime,
                                       Pageable pageable);

    // ========== PRIORITY-BASED QUERIES ==========

    /**
     * Find high-priority pending webhooks
     */
    @Query("SELECT w FROM Webhook w WHERE w.status = 'PENDING' " +
           "AND w.priority = :priority " +
           "ORDER BY w.createdAt ASC")
    List<Webhook> findPendingWebhooksByPriority(@Param("priority") WebhookPriority priority,
                                                  Pageable pageable);

    // ========== TIME-RANGE QUERIES ==========

    /**
     * Find webhooks created within time range
     */
    @Query("SELECT w FROM Webhook w WHERE w.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY w.createdAt DESC")
    Page<Webhook> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          Pageable pageable);

    /**
     * Find webhooks by event type and time range
     */
    @Query("SELECT w FROM Webhook w WHERE w.eventType = :eventType " +
           "AND w.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY w.createdAt DESC")
    Page<Webhook> findByEventTypeAndCreatedAtBetween(@Param("eventType") String eventType,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime,
                                                       Pageable pageable);

    // ========== STATISTICS QUERIES ==========

    /**
     * Count webhooks by status
     */
    @Query("SELECT COUNT(w) FROM Webhook w WHERE w.status = :status")
    long countByStatus(@Param("status") WebhookStatus status);

    /**
     * Count webhooks by subscription ID and status
     */
    long countBySubscriptionIdAndStatus(String subscriptionId, WebhookStatus status);

    /**
     * Count failed deliveries for subscription
     */
    @Query("SELECT COUNT(w) FROM Webhook w WHERE w.subscriptionId = :subscriptionId " +
           "AND w.status = 'FAILED'")
    long countFailedDeliveriesBySubscriptionId(@Param("subscriptionId") String subscriptionId);

    /**
     * Count successful deliveries for subscription
     */
    @Query("SELECT COUNT(w) FROM Webhook w WHERE w.subscriptionId = :subscriptionId " +
           "AND w.status = 'DELIVERED'")
    long countSuccessfulDeliveriesBySubscriptionId(@Param("subscriptionId") String subscriptionId);

    /**
     * Get success rate for subscription
     */
    @Query("SELECT CAST(COUNT(CASE WHEN w.status = 'DELIVERED' THEN 1 END) AS double) / " +
           "CAST(COUNT(w) AS double) * 100 " +
           "FROM Webhook w WHERE w.subscriptionId = :subscriptionId")
    Double getSuccessRateBySubscriptionId(@Param("subscriptionId") String subscriptionId);

    /**
     * Get average delivery time for subscription (in milliseconds)
     */
    @Query("SELECT AVG(w.totalDeliveryTimeMs) FROM Webhook w " +
           "WHERE w.subscriptionId = :subscriptionId " +
           "AND w.status = 'DELIVERED' " +
           "AND w.totalDeliveryTimeMs IS NOT NULL")
    Double getAverageDeliveryTimeBySubscriptionId(@Param("subscriptionId") String subscriptionId);

    // ========== BULK OPERATIONS ==========

    /**
     * Mark expired webhooks as failed
     */
    @Modifying
    @Query("UPDATE Webhook w SET w.status = 'FAILED', " +
           "w.failedAt = :now, " +
           "w.lastErrorMessage = 'Webhook expired after maximum retry time' " +
           "WHERE w.status IN ('PENDING', 'PENDING_RETRY') " +
           "AND w.createdAt < :cutoffTime")
    int markExpiredWebhooksAsFailed(@Param("now") LocalDateTime now,
                                     @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Delete old delivered webhooks (data retention)
     */
    @Modifying
    @Query("DELETE FROM Webhook w WHERE w.status = 'DELIVERED' " +
           "AND w.deliveredAt < :cutoffDate")
    int deleteOldDeliveredWebhooks(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Delete old failed webhooks (after moved to dead letter queue)
     */
    @Modifying
    @Query("DELETE FROM Webhook w WHERE w.status = 'FAILED' " +
           "AND w.failedAt < :cutoffDate")
    int deleteOldFailedWebhooks(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== MONITORING QUERIES ==========

    /**
     * Find stuck webhooks (in processing state too long)
     */
    @Query("SELECT w FROM Webhook w WHERE w.status = 'PROCESSING' " +
           "AND w.lastAttemptAt < :cutoffTime")
    List<Webhook> findStuckWebhooks(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find webhooks with high retry count (potential problematic endpoints)
     */
    @Query("SELECT w FROM Webhook w WHERE w.attemptCount >= :threshold " +
           "AND w.status IN ('PENDING_RETRY', 'FAILED') " +
           "ORDER BY w.attemptCount DESC")
    List<Webhook> findWebhooksWithHighRetryCount(@Param("threshold") int threshold,
                                                   Pageable pageable);

    /**
     * Get webhook queue depth by status
     */
    @Query("SELECT w.status as status, COUNT(w) as count FROM Webhook w " +
           "GROUP BY w.status")
    List<Object[]> getQueueDepthByStatus();

    /**
     * Get webhook statistics by event type
     */
    @Query("SELECT w.eventType as eventType, " +
           "COUNT(w) as total, " +
           "SUM(CASE WHEN w.status = 'DELIVERED' THEN 1 ELSE 0 END) as delivered, " +
           "SUM(CASE WHEN w.status = 'FAILED' THEN 1 ELSE 0 END) as failed " +
           "FROM Webhook w " +
           "WHERE w.createdAt >= :since " +
           "GROUP BY w.eventType")
    List<Object[]> getStatisticsByEventType(@Param("since") LocalDateTime since);

    // ========== ENDPOINT-SPECIFIC QUERIES ==========

    /**
     * Find recent failures for specific endpoint (for circuit breaker)
     */
    @Query("SELECT w FROM Webhook w WHERE w.endpointUrl = :endpointUrl " +
           "AND w.status = 'FAILED' " +
           "AND w.failedAt >= :since " +
           "ORDER BY w.failedAt DESC")
    List<Webhook> findRecentFailuresByEndpoint(@Param("endpointUrl") String endpointUrl,
                                                @Param("since") LocalDateTime since);

    /**
     * Count recent failures for endpoint (circuit breaker threshold check)
     */
    @Query("SELECT COUNT(w) FROM Webhook w WHERE w.endpointUrl = :endpointUrl " +
           "AND w.status = 'FAILED' " +
           "AND w.failedAt >= :since")
    long countRecentFailuresByEndpoint(@Param("endpointUrl") String endpointUrl,
                                        @Param("since") LocalDateTime since);

    /**
     * Find last successful delivery for endpoint
     */
    @Query("SELECT w FROM Webhook w WHERE w.endpointUrl = :endpointUrl " +
           "AND w.status = 'DELIVERED' " +
           "ORDER BY w.deliveredAt DESC")
    Optional<Webhook> findLastSuccessfulDeliveryByEndpoint(@Param("endpointUrl") String endpointUrl,
                                                             Pageable pageable);

    // ========== DEDUPLICATION ==========

    /**
     * Check if webhook with same event ID exists (idempotency)
     */
    boolean existsByEventId(String eventId);

    /**
     * Check if webhook with same event ID and subscription exists
     */
    boolean existsByEventIdAndSubscriptionId(String eventId, String subscriptionId);

    // ========== CLEANUP QUERIES ==========

    /**
     * Find webhooks older than retention period for cleanup
     */
    @Query("SELECT w FROM Webhook w WHERE w.createdAt < :cutoffDate " +
           "ORDER BY w.createdAt ASC")
    List<Webhook> findWebhooksOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate,
                                         Pageable pageable);
}
