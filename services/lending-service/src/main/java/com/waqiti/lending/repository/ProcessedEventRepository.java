package com.waqiti.lending.repository;

import com.waqiti.lending.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Processed Event entities (idempotency tracking)
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Check if event has been processed
     */
    boolean existsByEventId(String eventId);

    /**
     * Find processed event by event ID
     */
    Optional<ProcessedEvent> findByEventId(String eventId);

    /**
     * Find events by type
     */
    List<ProcessedEvent> findByEventTypeOrderByProcessedAtDesc(String eventType);

    /**
     * Find events by correlation ID
     */
    List<ProcessedEvent> findByCorrelationIdOrderByProcessedAtDesc(String correlationId);

    /**
     * Delete expired events (cleanup for TTL)
     */
    @Modifying
    @Query("DELETE FROM ProcessedEvent pe WHERE pe.expiresAt < :now")
    int deleteExpiredEvents(@Param("now") Instant now);

    /**
     * Count processed events by type
     */
    long countByEventType(String eventType);

    /**
     * Find recent processed events
     */
    @Query("SELECT pe FROM ProcessedEvent pe WHERE pe.processedAt >= :since ORDER BY pe.processedAt DESC")
    List<ProcessedEvent> findRecentlyProcessedEvents(@Param("since") Instant since);
}
