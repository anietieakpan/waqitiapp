package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.EventProcessingRecord;
import com.waqiti.wallet.domain.EventProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventProcessingRecordRepository extends JpaRepository<EventProcessingRecord, UUID> {

    /**
     * Find event processing record by event ID and type (for idempotency check)
     */
    Optional<EventProcessingRecord> findByEventIdAndEventType(String eventId, String eventType);

    /**
     * Check if event has been processed successfully
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM EventProcessingRecord e " +
           "WHERE e.eventId = :eventId AND e.eventType = :eventType AND e.status = 'COMPLETED'")
    boolean existsCompletedEvent(@Param("eventId") String eventId, @Param("eventType") String eventType);

    /**
     * Find events by correlation ID
     */
    List<EventProcessingRecord> findByCorrelationId(String correlationId);

    /**
     * Find events by status
     */
    List<EventProcessingRecord> findByStatus(EventProcessingStatus status);

    /**
     * Find failed events for retry
     */
    @Query("SELECT e FROM EventProcessingRecord e WHERE e.status = 'FAILED' " +
           "AND e.retryCount < :maxRetries AND e.failedAt < :retryAfter")
    List<EventProcessingRecord> findFailedEventsForRetry(@Param("maxRetries") Integer maxRetries,
                                                          @Param("retryAfter") LocalDateTime retryAfter);

    /**
     * Clean up old completed events
     */
    @Modifying
    @Query("DELETE FROM EventProcessingRecord e WHERE e.status = 'COMPLETED' AND e.processedAt < :before")
    int deleteOldCompletedEvents(@Param("before") LocalDateTime before);

    /**
     * Count events by status
     */
    Long countByStatus(EventProcessingStatus status);
}
