package com.waqiti.common.webhook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for webhook events with advanced querying capabilities
 */
@Repository
public interface WebhookRepository extends JpaRepository<WebhookEvent, String> {
    
    /**
     * Find webhooks ready for retry
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.status = 'PENDING' " +
           "AND (w.nextAttemptAt IS NULL OR w.nextAttemptAt <= :now) " +
           "AND w.retryCount < w.maxRetryAttempts " +
           "AND (w.expiresAt IS NULL OR w.expiresAt > :now) " +
           "ORDER BY w.priority DESC, w.createdAt ASC")
    List<WebhookEvent> findReadyForRetry(@Param("now") Instant now, Pageable pageable);
    
    /**
     * Find webhooks by status
     */
    List<WebhookEvent> findByStatusOrderByCreatedAtDesc(WebhookEvent.WebhookStatus status);
    
    /**
     * Find webhooks by status with pagination
     */
    Page<WebhookEvent> findByStatus(WebhookEvent.WebhookStatus status, Pageable pageable);
    
    /**
     * Find webhooks by event type
     */
    List<WebhookEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
    
    /**
     * Find webhooks by endpoint URL
     */
    List<WebhookEvent> findByEndpointUrlOrderByCreatedAtDesc(String endpointUrl);
    
    /**
     * Find webhooks by idempotency key
     */
    Optional<WebhookEvent> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Find webhooks by source service
     */
    List<WebhookEvent> findBySourceServiceOrderByCreatedAtDesc(String sourceService);
    
    /**
     * Find webhooks created within time range
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY w.createdAt DESC")
    List<WebhookEvent> findByCreatedAtBetween(@Param("startTime") Instant startTime, 
                                             @Param("endTime") Instant endTime);
    
    /**
     * Find expired webhooks
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.expiresAt <= :now " +
           "AND w.status NOT IN ('DELIVERED', 'EXPIRED', 'CANCELLED', 'DEAD_LETTER')")
    List<WebhookEvent> findExpiredWebhooks(@Param("now") Instant now);
    
    /**
     * Find failed webhooks ready for dead letter processing
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.status = 'FAILED' " +
           "AND w.retryCount >= w.maxRetryAttempts")
    List<WebhookEvent> findFailedWebhooksForDeadLetter();
    
    /**
     * Find webhooks by priority
     */
    List<WebhookEvent> findByPriorityOrderByCreatedAtDesc(WebhookEvent.WebhookPriority priority);
    
    /**
     * Count webhooks by status
     */
    long countByStatus(WebhookEvent.WebhookStatus status);
    
    /**
     * Count webhooks by event type
     */
    long countByEventType(String eventType);
    
    /**
     * Count webhooks by endpoint URL
     */
    long countByEndpointUrl(String endpointUrl);
    
    /**
     * Get webhook statistics
     */
    @Query("SELECT w.status, COUNT(w) FROM WebhookEvent w GROUP BY w.status")
    List<Object[]> getWebhookStatsByStatus();
    
    /**
     * Get webhook statistics by event type
     */
    @Query("SELECT w.eventType, COUNT(w) FROM WebhookEvent w GROUP BY w.eventType ORDER BY COUNT(w) DESC")
    List<Object[]> getWebhookStatsByEventType();
    
    /**
     * Get webhook statistics by endpoint
     */
    @Query("SELECT w.endpointUrl, COUNT(w), AVG(w.retryCount) FROM WebhookEvent w " +
           "GROUP BY w.endpointUrl ORDER BY COUNT(w) DESC")
    List<Object[]> getWebhookStatsByEndpoint();
    
    /**
     * Find webhooks needing cleanup (old delivered/failed webhooks)
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.status IN ('DELIVERED', 'FAILED', 'EXPIRED', 'CANCELLED') " +
           "AND w.updatedAt < :cutoffTime")
    List<WebhookEvent> findWebhooksForCleanup(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Update webhook status in batch
     */
    @Modifying
    @Query("UPDATE WebhookEvent w SET w.status = :newStatus WHERE w.status = :oldStatus " +
           "AND w.updatedAt < :cutoffTime")
    int updateWebhookStatus(@Param("oldStatus") WebhookEvent.WebhookStatus oldStatus,
                           @Param("newStatus") WebhookEvent.WebhookStatus newStatus,
                           @Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Delete old webhooks in batch
     */
    @Modifying
    @Query("DELETE FROM WebhookEvent w WHERE w.status IN ('DELIVERED', 'FAILED', 'EXPIRED', 'CANCELLED') " +
           "AND w.updatedAt < :cutoffTime")
    int deleteOldWebhooks(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Reset stuck processing webhooks
     */
    @Modifying
    @Query("UPDATE WebhookEvent w SET w.status = 'PENDING' WHERE w.status = 'PROCESSING' " +
           "AND w.updatedAt < :stuckTimeout")
    int resetStuckWebhooks(@Param("stuckTimeout") Instant stuckTimeout);
    
    /**
     * Find webhooks with high retry count
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.retryCount >= :minRetryCount " +
           "ORDER BY w.retryCount DESC, w.createdAt ASC")
    List<WebhookEvent> findHighRetryCountWebhooks(@Param("minRetryCount") int minRetryCount);
    
    /**
     * Find webhooks by multiple statuses
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.status IN :statuses ORDER BY w.createdAt DESC")
    List<WebhookEvent> findByStatusIn(@Param("statuses") List<WebhookEvent.WebhookStatus> statuses);
    
    /**
     * Find webhooks by tag
     */
    @Query("SELECT w FROM WebhookEvent w JOIN w.tags t WHERE t = :tag ORDER BY w.createdAt DESC")
    List<WebhookEvent> findByTag(@Param("tag") String tag);
    
    /**
     * Get delivery success rate by endpoint
     */
    @Query("SELECT w.endpointUrl, " +
           "SUM(CASE WHEN w.status = 'DELIVERED' THEN 1 ELSE 0 END) as delivered, " +
           "COUNT(w) as total, " +
           "CAST(SUM(CASE WHEN w.status = 'DELIVERED' THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(w) as successRate " +
           "FROM WebhookEvent w " +
           "WHERE w.createdAt >= :since " +
           "GROUP BY w.endpointUrl " +
           "ORDER BY successRate ASC")
    List<Object[]> getDeliverySuccessRateByEndpoint(@Param("since") Instant since);
    
    /**
     * Get average retry count by event type
     */
    @Query("SELECT w.eventType, AVG(w.retryCount), COUNT(w) FROM WebhookEvent w " +
           "WHERE w.createdAt >= :since " +
           "GROUP BY w.eventType " +
           "ORDER BY AVG(w.retryCount) DESC")
    List<Object[]> getAverageRetryCountByEventType(@Param("since") Instant since);
    
    /**
     * Find webhooks with specific metadata
     */
    @Query("SELECT w FROM WebhookEvent w WHERE JSON_EXTRACT(w.metadata, :jsonPath) = :value")
    List<WebhookEvent> findByMetadata(@Param("jsonPath") String jsonPath, @Param("value") String value);

    /**
     * Save dead letter webhook
     */
    default void saveDeadLetter(DeadLetterWebhook deadLetter) {
        // Implementation should be in a separate DeadLetterRepository
        // For now, update the event status
        if (deadLetter.getEvent() != null) {
            WebhookEvent event = deadLetter.getEvent();
            event.setStatus(WebhookEvent.WebhookStatus.DEAD_LETTER);
            save(event);
        }
    }

    /**
     * Find dead letters ready for retry
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.status = 'DEAD_LETTER'")
    List<DeadLetterWebhook> findDeadLettersToRetry();
}