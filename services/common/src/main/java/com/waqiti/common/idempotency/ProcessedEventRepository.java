package com.waqiti.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ProcessedEvent - Database-Level Idempotency
 *
 * CRITICAL: This repository provides atomic operations for idempotency enforcement.
 * All queries use pessimistic locking where appropriate to prevent race conditions.
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-11-08
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Find event by eventId with pessimistic lock
     * This prevents concurrent modifications during duplicate detection
     *
     * @param eventId Unique event identifier
     * @return Optional<ProcessedEvent>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pe FROM ProcessedEvent pe WHERE pe.eventId = :eventId")
    Optional<ProcessedEvent> findByEventIdWithLock(@Param("eventId") String eventId);

    /**
     * Find event by eventId (read-only, no lock)
     *
     * @param eventId Unique event identifier
     * @return Optional<ProcessedEvent>
     */
    Optional<ProcessedEvent> findByEventId(String eventId);

    /**
     * Check if event exists (fast existence check)
     *
     * @param eventId Unique event identifier
     * @return true if event already processed
     */
    boolean existsByEventId(String eventId);

    /**
     * Find all stale PROCESSING events (likely crashed/hung processes)
     * Used for cleanup and recovery
     *
     * @param status Processing status
     * @param createdBefore Instant before which events are considered stale
     * @return List of stale events
     */
    @Query("SELECT pe FROM ProcessedEvent pe WHERE pe.status = :status AND pe.createdAt < :createdBefore")
    List<ProcessedEvent> findStaleProcessingEvents(
        @Param("status") ProcessedEvent.ProcessingStatus status,
        @Param("createdBefore") Instant createdBefore
    );

    /**
     * Find all FAILED events eligible for retry
     *
     * @param status Processing status
     * @param maxRetries Maximum number of retries allowed
     * @return List of failed events
     */
    @Query("SELECT pe FROM ProcessedEvent pe WHERE pe.status = :status AND pe.retryCount < :maxRetries")
    List<ProcessedEvent> findFailedEventsEligibleForRetry(
        @Param("status") ProcessedEvent.ProcessingStatus status,
        @Param("maxRetries") int maxRetries
    );

    /**
     * Update status for a specific event
     * Used for marking events as COMPLETED or FAILED
     *
     * @param eventId Event identifier
     * @param status New status
     * @return Number of rows updated
     */
    @Modifying
    @Query("UPDATE ProcessedEvent pe SET pe.status = :status, pe.completedAt = :completedAt WHERE pe.eventId = :eventId")
    int updateStatus(
        @Param("eventId") String eventId,
        @Param("status") ProcessedEvent.ProcessingStatus status,
        @Param("completedAt") Instant completedAt
    );

    /**
     * Delete old completed events for cleanup (data retention policy)
     * Typically run as scheduled job to prevent table growth
     *
     * @param status Processing status
     * @param completedBefore Instant before which events should be deleted
     * @return Number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM ProcessedEvent pe WHERE pe.status = :status AND pe.completedAt < :completedBefore")
    int deleteOldCompletedEvents(
        @Param("status") ProcessedEvent.ProcessingStatus status,
        @Param("completedBefore") Instant completedBefore
    );

    /**
     * Count events by status (for metrics and monitoring)
     *
     * @param status Processing status
     * @return Count of events
     */
    long countByStatus(ProcessedEvent.ProcessingStatus status);

    /**
     * Count events by consumer name and status (for per-consumer metrics)
     *
     * @param consumerName Consumer name
     * @param status Processing status
     * @return Count of events
     */
    long countByConsumerNameAndStatus(String consumerName, ProcessedEvent.ProcessingStatus status);

    /**
     * Find recent events for a specific consumer (for debugging/monitoring)
     *
     * @param consumerName Consumer name
     * @param since Instant from which to retrieve events
     * @return List of recent events
     */
    @Query("SELECT pe FROM ProcessedEvent pe WHERE pe.consumerName = :consumerName AND pe.createdAt >= :since ORDER BY pe.createdAt DESC")
    List<ProcessedEvent> findRecentEventsByConsumer(
        @Param("consumerName") String consumerName,
        @Param("since") Instant since
    );

    /**
     * Find events by entity ID (for correlation across services)
     * Example: Find all processed events related to payment ID "123"
     *
     * @param entityId Business entity identifier
     * @return List of processed events
     */
    List<ProcessedEvent> findByEntityId(String entityId);

    /**
     * Find events by entity type and status (for monitoring specific event types)
     *
     * @param entityType Type of entity (PAYMENT, TRANSACTION, etc.)
     * @param status Processing status
     * @return List of processed events
     */
    List<ProcessedEvent> findByEntityTypeAndStatus(String entityType, ProcessedEvent.ProcessingStatus status);

    /**
     * Calculate average processing duration for a consumer (for performance monitoring)
     *
     * @param consumerName Consumer name
     * @param since Calculate average since this instant
     * @return Average duration in milliseconds
     */
    @Query("SELECT AVG(pe.processingDurationMs) FROM ProcessedEvent pe WHERE pe.consumerName = :consumerName AND pe.status = 'COMPLETED' AND pe.createdAt >= :since")
    Double calculateAverageProcessingDuration(
        @Param("consumerName") String consumerName,
        @Param("since") Instant since
    );

    /**
     * Find duplicate events (same eventId, multiple processing attempts)
     * Used for detecting idempotency violations or retry patterns
     *
     * @return List of event IDs that appear multiple times
     */
    @Query("SELECT pe.eventId FROM ProcessedEvent pe GROUP BY pe.eventId HAVING COUNT(pe.eventId) > 1")
    List<String> findDuplicateEventIds();
}
