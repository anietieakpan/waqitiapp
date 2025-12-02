package com.waqiti.crypto.lightning.repository;

import com.waqiti.crypto.lightning.entity.WebhookDeliveryEntity;
import com.waqiti.crypto.lightning.entity.WebhookDeliveryStatus;
import com.waqiti.crypto.lightning.entity.WebhookEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Lightning webhook delivery entities
 */
@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, String> {

    /**
     * Find deliveries by webhook ID
     */
    List<WebhookDeliveryEntity> findByWebhookId(String webhookId);

    /**
     * Find deliveries by webhook ID with pagination
     */
    Page<WebhookDeliveryEntity> findByWebhookId(String webhookId, Pageable pageable);

    /**
     * Find deliveries by status
     */
    List<WebhookDeliveryEntity> findByStatus(WebhookDeliveryStatus status);

    /**
     * Find pending deliveries that need retry
     */
    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.status = 'PENDING' " +
           "AND d.attemptCount < :maxAttempts " +
           "AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now) " +
           "ORDER BY d.createdAt ASC")
    List<WebhookDeliveryEntity> findPendingDeliveries(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    /**
     * Find pending deliveries with limit
     */
    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.status = 'PENDING' " +
           "AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now) " +
           "ORDER BY d.createdAt ASC LIMIT :limit")
    List<WebhookDeliveryEntity> findPendingDeliveries(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * Find deliveries by webhook and status
     */
    List<WebhookDeliveryEntity> findByWebhookIdAndStatus(String webhookId, WebhookDeliveryStatus status);

    /**
     * Find deliveries by event type
     */
    List<WebhookDeliveryEntity> findByEventType(WebhookEventType eventType);

    /**
     * Find deliveries within time range
     */
    List<WebhookDeliveryEntity> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * Find failed deliveries
     */
    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.status = 'FAILED' " +
           "ORDER BY d.createdAt DESC")
    List<WebhookDeliveryEntity> findFailedDeliveries(Pageable pageable);

    /**
     * Count deliveries by status
     */
    long countByStatus(WebhookDeliveryStatus status);

    /**
     * Count deliveries by webhook and status
     */
    long countByWebhookIdAndStatus(String webhookId, WebhookDeliveryStatus status);

    /**
     * Get delivery statistics by webhook
     */
    @Query("SELECT d.status, COUNT(d), AVG(d.attemptCount), AVG(d.durationMs) " +
           "FROM WebhookDeliveryEntity d WHERE d.webhookId = :webhookId GROUP BY d.status")
    List<Object[]> getWebhookDeliveryStatistics(@Param("webhookId") String webhookId);

    /**
     * Get overall delivery statistics
     */
    @Query("SELECT d.status, COUNT(d), AVG(d.attemptCount), AVG(d.durationMs) " +
           "FROM WebhookDeliveryEntity d GROUP BY d.status")
    List<Object[]> getOverallDeliveryStatistics();

    /**
     * Find deliveries with high attempt count
     */
    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.attemptCount > :threshold " +
           "ORDER BY d.attemptCount DESC")
    List<WebhookDeliveryEntity> findDeliveriesWithHighAttempts(@Param("threshold") int threshold);

    /**
     * Find slow deliveries
     */
    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.durationMs > :thresholdMs " +
           "AND d.status = 'DELIVERED' ORDER BY d.durationMs DESC")
    List<WebhookDeliveryEntity> findSlowDeliveries(@Param("thresholdMs") long thresholdMs);

    /**
     * Delete old deliveries
     */
    @Modifying
    @Query("DELETE FROM WebhookDeliveryEntity d WHERE d.createdAt < :cutoff")
    int deleteOldDeliveries(@Param("cutoff") Instant cutoff);

    /**
     * Delete deliveries by webhook ID
     */
    @Modifying
    void deleteByWebhookId(String webhookId);

    /**
     * Find recent successful deliveries for webhook
     */
    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.webhookId = :webhookId " +
           "AND d.status = 'DELIVERED' AND d.deliveredAt >= :since " +
           "ORDER BY d.deliveredAt DESC")
    List<WebhookDeliveryEntity> findRecentSuccessfulDeliveries(
        @Param("webhookId") String webhookId, 
        @Param("since") Instant since
    );

    /**
     * Find deliveries by response code
     */
    List<WebhookDeliveryEntity> findByResponseCode(int responseCode);

    /**
     * Find deliveries that took longer than threshold
     */
    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.durationMs IS NOT NULL " +
           "AND d.durationMs > :durationMs ORDER BY d.durationMs DESC")
    List<WebhookDeliveryEntity> findByDurationGreaterThan(@Param("durationMs") long durationMs);

    /**
     * Get success rate for webhook
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN d.status = 'DELIVERED' THEN 1 END) as successful, " +
           "COUNT(d) as total " +
           "FROM WebhookDeliveryEntity d WHERE d.webhookId = :webhookId")
    Object[] getWebhookSuccessRate(@Param("webhookId") String webhookId);

    /**
     * Get average delivery time for webhook
     */
    @Query("SELECT AVG(d.durationMs) FROM WebhookDeliveryEntity d " +
           "WHERE d.webhookId = :webhookId AND d.status = 'DELIVERED' AND d.durationMs IS NOT NULL")
    Double getAverageDeliveryTime(@Param("webhookId") String webhookId);

    /**
     * Find deliveries requiring attention (many retries, long duration, etc.)
     */
    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE " +
           "(d.status = 'PENDING' AND d.attemptCount > 2) OR " +
           "(d.status = 'DELIVERED' AND d.durationMs > 10000) OR " +
           "(d.status = 'FAILED' AND d.attemptCount >= 3)")
    List<WebhookDeliveryEntity> findDeliveriesRequiringAttention();

    /**
     * Get hourly delivery statistics
     */
    @Query("SELECT " +
           "FUNCTION('DATE_FORMAT', d.createdAt, '%Y-%m-%d %H:00:00') as hour, " +
           "d.status, " +
           "COUNT(d) " +
           "FROM WebhookDeliveryEntity d " +
           "WHERE d.createdAt >= :since " +
           "GROUP BY hour, d.status " +
           "ORDER BY hour DESC")
    List<Object[]> getHourlyDeliveryStats(@Param("since") Instant since);
}