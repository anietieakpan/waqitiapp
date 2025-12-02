package com.waqiti.payment.webhook.repository;

import com.waqiti.payment.webhook.WebhookDeliveryAttempt;
import com.waqiti.payment.webhook.WebhookDeliveryStatus;
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
 * Repository for webhook delivery attempts
 */
@Repository
public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, Long> {

    /**
     * Count attempts for a specific event and endpoint
     */
    int countByEventIdAndEndpointUrl(String eventId, String endpointUrl);

    /**
     * Find all attempts for a specific event
     */
    List<WebhookDeliveryAttempt> findByEventIdOrderByAttemptNumberDesc(String eventId);

    /**
     * Find attempts by status
     */
    List<WebhookDeliveryAttempt> findByStatus(WebhookDeliveryStatus status);

    /**
     * Find failed attempts that should be retried
     */
    @Query("SELECT w FROM WebhookDeliveryAttempt w WHERE w.status = :status " +
           "AND w.attemptedAt > :cutoffTime ORDER BY w.attemptedAt ASC")
    List<WebhookDeliveryAttempt> findFailedAttemptsForRetry(
        @Param("status") WebhookDeliveryStatus status,
        @Param("cutoffTime") Instant cutoffTime);

    /**
     * Find attempts by endpoint URL
     */
    Page<WebhookDeliveryAttempt> findByEndpointUrlOrderByAttemptedAtDesc(
        String endpointUrl, Pageable pageable);

    /**
     * Find attempts by event type
     */
    List<WebhookDeliveryAttempt> findByEventTypeOrderByAttemptedAtDesc(String eventType);

    /**
     * Find successful attempts for analytics
     */
    @Query("SELECT w FROM WebhookDeliveryAttempt w WHERE w.status = 'SUCCESS' " +
           "AND w.completedAt BETWEEN :startTime AND :endTime")
    List<WebhookDeliveryAttempt> findSuccessfulAttemptsBetween(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);

    /**
     * Get delivery statistics
     */
    @Query("SELECT w.status, COUNT(w) FROM WebhookDeliveryAttempt w " +
           "WHERE w.attemptedAt >= :since GROUP BY w.status")
    List<Object[]> getDeliveryStatistics(@Param("since") Instant since);

    /**
     * Find attempts with slow response times
     */
    @Query("SELECT w FROM WebhookDeliveryAttempt w WHERE w.responseTimeMs > :threshold " +
           "AND w.completedAt >= :since ORDER BY w.responseTimeMs DESC")
    List<WebhookDeliveryAttempt> findSlowAttempts(
        @Param("threshold") long thresholdMs,
        @Param("since") Instant since);

    /**
     * Delete old attempts for cleanup
     */
    @Modifying
    @Query("DELETE FROM WebhookDeliveryAttempt w WHERE w.attemptedAt < :cutoffTime")
    int deleteOldAttempts(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Find attempts by endpoint and status
     */
    List<WebhookDeliveryAttempt> findByEndpointUrlAndStatusOrderByAttemptedAtDesc(
        String endpointUrl, WebhookDeliveryStatus status);

    /**
     * Get the last successful attempt for an event and endpoint
     */
    @Query("SELECT w FROM WebhookDeliveryAttempt w WHERE w.eventId = :eventId " +
           "AND w.endpointUrl = :endpointUrl AND w.status = 'SUCCESS' " +
           "ORDER BY w.completedAt DESC")
    List<WebhookDeliveryAttempt> findLastSuccessfulAttempt(
        @Param("eventId") String eventId,
        @Param("endpointUrl") String endpointUrl);

    /**
     * Check if event was successfully delivered to endpoint
     */
    @Query("SELECT COUNT(w) > 0 FROM WebhookDeliveryAttempt w WHERE w.eventId = :eventId " +
           "AND w.endpointUrl = :endpointUrl AND w.status = 'SUCCESS'")
    boolean hasSuccessfulDelivery(
        @Param("eventId") String eventId,
        @Param("endpointUrl") String endpointUrl);

    /**
     * Get average response time for an endpoint
     */
    @Query("SELECT AVG(w.responseTimeMs) FROM WebhookDeliveryAttempt w " +
           "WHERE w.endpointUrl = :endpointUrl AND w.status = 'SUCCESS' " +
           "AND w.completedAt >= :since")
    Double getAverageResponseTime(
        @Param("endpointUrl") String endpointUrl,
        @Param("since") Instant since);

    /**
     * Get failure rate for an endpoint
     */
    @Query("SELECT (COUNT(CASE WHEN w.status = 'FAILED' THEN 1 END) * 100.0 / COUNT(w)) " +
           "FROM WebhookDeliveryAttempt w WHERE w.endpointUrl = :endpointUrl " +
           "AND w.attemptedAt >= :since")
    Double getFailureRate(
        @Param("endpointUrl") String endpointUrl,
        @Param("since") Instant since);

    /**
     * Find endpoints with high failure rates
     */
    @Query("SELECT w.endpointUrl, " +
           "(COUNT(CASE WHEN w.status = 'FAILED' THEN 1 END) * 100.0 / COUNT(w)) as failureRate " +
           "FROM WebhookDeliveryAttempt w WHERE w.attemptedAt >= :since " +
           "GROUP BY w.endpointUrl HAVING failureRate > :threshold " +
           "ORDER BY failureRate DESC")
    List<Object[]> findEndpointsWithHighFailureRate(
        @Param("since") Instant since,
        @Param("threshold") double threshold);
}