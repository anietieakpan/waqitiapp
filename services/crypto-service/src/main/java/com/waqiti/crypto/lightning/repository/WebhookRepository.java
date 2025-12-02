package com.waqiti.crypto.lightning.repository;

import com.waqiti.crypto.lightning.entity.WebhookEntity;
import com.waqiti.crypto.lightning.entity.WebhookEventType;
import com.waqiti.crypto.lightning.entity.WebhookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Lightning webhook entities
 */
@Repository
public interface WebhookRepository extends JpaRepository<WebhookEntity, String> {

    /**
     * Find webhook by ID and user ID
     */
    Optional<WebhookEntity> findByIdAndUserId(String id, String userId);

    /**
     * Find webhooks by user ID
     */
    List<WebhookEntity> findByUserId(String userId);

    /**
     * Find active webhooks by user ID
     */
    List<WebhookEntity> findByUserIdAndStatus(String userId, WebhookStatus status);

    /**
     * Find webhooks by user ID with pagination
     */
    Page<WebhookEntity> findByUserId(String userId, Pageable pageable);

    /**
     * Find webhooks by user ID and event type
     */
    @Query("SELECT w FROM WebhookEntity w JOIN w.events e WHERE w.userId = :userId AND e = :eventType AND w.status = 'ACTIVE'")
    List<WebhookEntity> findByUserIdAndEventType(@Param("userId") String userId, @Param("eventType") WebhookEventType eventType);

    /**
     * Find webhook by payment hash
     */
    Optional<WebhookEntity> findByPaymentHash(String paymentHash);

    /**
     * Find expired webhooks
     */
    @Query("SELECT w FROM WebhookEntity w WHERE w.expiresAt IS NOT NULL AND w.expiresAt < :now AND w.status = 'ACTIVE'")
    List<WebhookEntity> findExpiredWebhooks(@Param("now") Instant now);

    /**
     * Find webhooks by status
     */
    List<WebhookEntity> findByStatus(WebhookStatus status);

    /**
     * Find webhooks that need suspension (too many failures)
     */
    @Query("SELECT w FROM WebhookEntity w WHERE w.status = 'ACTIVE' AND w.failureCount > 50")
    List<WebhookEntity> findWebhooksNeedingSuspension();

    /**
     * Find webhooks with low success rate
     */
    @Query("SELECT w FROM WebhookEntity w WHERE w.status = 'ACTIVE' AND w.deliveryCount > 10 " +
           "AND (CAST(w.deliveryCount - w.failureCount AS double) / w.deliveryCount) < 0.1")
    List<WebhookEntity> findWebhooksWithLowSuccessRate();

    /**
     * Count webhooks by user and status
     */
    long countByUserIdAndStatus(String userId, WebhookStatus status);

    /**
     * Count total webhooks by status
     */
    long countByStatus(WebhookStatus status);

    /**
     * Find webhooks by URL pattern
     */
    List<WebhookEntity> findByUrlContaining(String urlPattern);

    /**
     * Find webhooks that haven't been used recently
     */
    @Query("SELECT w FROM WebhookEntity w WHERE w.status = 'ACTIVE' " +
           "AND (w.lastSuccessAt IS NULL OR w.lastSuccessAt < :cutoff)")
    List<WebhookEntity> findUnusedWebhooks(@Param("cutoff") Instant cutoff);

    /**
     * Find webhooks created within time range
     */
    List<WebhookEntity> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * Find webhooks supporting specific event type
     */
    @Query("SELECT w FROM WebhookEntity w JOIN w.events e WHERE e = :eventType AND w.status = 'ACTIVE'")
    List<WebhookEntity> findBySupportedEventType(@Param("eventType") WebhookEventType eventType);

    /**
     * Get webhook statistics grouped by status
     */
    @Query("SELECT w.status, COUNT(w), AVG(w.deliveryCount), AVG(w.failureCount) " +
           "FROM WebhookEntity w GROUP BY w.status")
    List<Object[]> getWebhookStatistics();

    /**
     * Get user webhook statistics
     */
    @Query("SELECT w.status, COUNT(w), SUM(w.deliveryCount), SUM(w.failureCount) " +
           "FROM WebhookEntity w WHERE w.userId = :userId GROUP BY w.status")
    List<Object[]> getUserWebhookStatistics(@Param("userId") String userId);

    /**
     * Find webhooks by multiple event types
     */
    @Query("SELECT DISTINCT w FROM WebhookEntity w JOIN w.events e WHERE e IN :eventTypes AND w.status = 'ACTIVE'")
    List<WebhookEntity> findByEventTypes(@Param("eventTypes") List<WebhookEventType> eventTypes);

    /**
     * Check if webhook exists for user and URL
     */
    boolean existsByUserIdAndUrl(String userId, String url);
}