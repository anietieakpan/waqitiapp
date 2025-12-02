package com.waqiti.common.kafka.dlq.repository;

import com.waqiti.common.kafka.dlq.entity.DeadLetterEvent;
import com.waqiti.common.kafka.dlq.entity.DeadLetterEvent.DLQSeverity;
import com.waqiti.common.kafka.dlq.entity.DeadLetterEvent.DLQStatus;
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
 * Dead Letter Event Repository
 *
 * Provides data access for DLQ (Dead Letter Queue) events.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-11
 */
@Repository
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {

    /**
     * Find DLQ event by original event ID
     *
     * @param eventId Original event ID
     * @return Optional DeadLetterEvent
     */
    Optional<DeadLetterEvent> findByEventId(String eventId);

    /**
     * Check if event already exists in DLQ
     *
     * @param eventId Original event ID
     * @return true if exists
     */
    boolean existsByEventId(String eventId);

    /**
     * Find all events by status
     *
     * @param status DLQ status
     * @param pageable Pagination
     * @return Page of events
     */
    Page<DeadLetterEvent> findByStatus(DLQStatus status, Pageable pageable);

    /**
     * Find all events by service name
     *
     * @param serviceName Service name
     * @param pageable Pagination
     * @return Page of events
     */
    Page<DeadLetterEvent> findByServiceName(String serviceName, Pageable pageable);

    /**
     * Find all events by event type
     *
     * @param eventType Event type (class name)
     * @param pageable Pagination
     * @return Page of events
     */
    Page<DeadLetterEvent> findByEventType(String eventType, Pageable pageable);

    /**
     * Find all events by severity
     *
     * @param severity Severity level
     * @param pageable Pagination
     * @return Page of events
     */
    Page<DeadLetterEvent> findBySeverity(DLQSeverity severity, Pageable pageable);

    /**
     * Find events eligible for retry
     *
     * @param now Current timestamp
     * @return List of events ready for retry
     */
    @Query("SELECT d FROM DeadLetterEvent d WHERE " +
           "d.status IN ('NEW', 'RETRYING') AND " +
           "d.retryCount < d.maxRetries AND " +
           "(d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)")
    List<DeadLetterEvent> findEventsEligibleForRetry(@Param("now") LocalDateTime now);

    /**
     * Find expired events for cleanup
     *
     * @param now Current timestamp
     * @return List of expired events
     */
    @Query("SELECT d FROM DeadLetterEvent d WHERE d.expiresAt IS NOT NULL AND d.expiresAt <= :now")
    List<DeadLetterEvent> findExpiredEvents(@Param("now") LocalDateTime now);

    /**
     * Count events by status
     *
     * @param status DLQ status
     * @return Count
     */
    long countByStatus(DLQStatus status);

    /**
     * Count events by severity
     *
     * @param severity Severity level
     * @return Count
     */
    long countBySeverity(DLQSeverity severity);

    /**
     * Count events by service name
     *
     * @param serviceName Service name
     * @return Count
     */
    long countByServiceName(String serviceName);

    /**
     * Find recent critical events
     *
     * @param severity Severity level
     * @param since Timestamp threshold
     * @param pageable Pagination
     * @return Page of critical events
     */
    @Query("SELECT d FROM DeadLetterEvent d WHERE " +
           "d.severity = :severity AND " +
           "d.createdAt >= :since " +
           "ORDER BY d.createdAt DESC")
    Page<DeadLetterEvent> findRecentBySeverity(
        @Param("severity") DLQSeverity severity,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );

    /**
     * Find events failing repeatedly (high retry count)
     *
     * @param minRetries Minimum retry count threshold
     * @param pageable Pagination
     * @return Page of problematic events
     */
    @Query("SELECT d FROM DeadLetterEvent d WHERE " +
           "d.retryCount >= :minRetries AND " +
           "d.status IN ('NEW', 'RETRYING') " +
           "ORDER BY d.retryCount DESC, d.createdAt ASC")
    Page<DeadLetterEvent> findHighRetryEvents(
        @Param("minRetries") int minRetries,
        Pageable pageable
    );

    /**
     * Delete expired events (cleanup)
     *
     * @param expiryDate Expiry threshold
     * @return Number of deleted records
     */
    @Modifying
    @Query("DELETE FROM DeadLetterEvent d WHERE d.expiresAt IS NOT NULL AND d.expiresAt <= :expiryDate")
    int deleteExpiredEvents(@Param("expiryDate") LocalDateTime expiryDate);

    /**
     * Delete resolved events older than retention period
     *
     * @param retentionDate Retention threshold
     * @return Number of deleted records
     */
    @Modifying
    @Query("DELETE FROM DeadLetterEvent d WHERE " +
           "d.status IN ('RESOLVED', 'MANUALLY_RESOLVED', 'SKIPPED') AND " +
           "d.resolvedAt IS NOT NULL AND d.resolvedAt <= :retentionDate")
    int deleteOldResolvedEvents(@Param("retentionDate") LocalDateTime retentionDate);

    /**
     * Get DLQ statistics
     *
     * @return Statistics map
     */
    @Query("SELECT " +
           "d.status as status, " +
           "d.severity as severity, " +
           "COUNT(d) as count " +
           "FROM DeadLetterEvent d " +
           "GROUP BY d.status, d.severity")
    List<Object[]> getDLQStatistics();
}
