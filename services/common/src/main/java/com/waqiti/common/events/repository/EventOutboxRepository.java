package com.waqiti.common.events.repository;

import com.waqiti.common.events.model.EventOutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for event outbox pattern implementation
 * Handles persistent storage of events for reliable messaging
 */
@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntry, UUID> {
    
    /**
     * Find all failed events that need retry
     */
    @Query("SELECT e FROM EventOutboxEntry e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetries")
    List<EventOutboxEntry> findFailedEvents(@Param("maxRetries") int maxRetries);
    
    /**
     * Find all failed events with default retry limit
     */
    @Query("SELECT e FROM EventOutboxEntry e WHERE e.status = 'FAILED' AND e.retryCount < 3")
    List<EventOutboxEntry> findFailedEvents();
    
    /**
     * Find events pending processing
     */
    @Query("SELECT e FROM EventOutboxEntry e WHERE e.status = 'PENDING' AND e.createdAt < :cutoffTime")
    List<EventOutboxEntry> findPendingEvents(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find events by aggregate ID
     */
    List<EventOutboxEntry> findByAggregateId(String aggregateId);
    
    /**
     * Find events by topic
     */
    List<EventOutboxEntry> findByTopic(String topic);
    
    /**
     * Find events by status
     */
    List<EventOutboxEntry> findByStatus(String status);
    
    /**
     * Count failed events
     */
    long countByStatus(String status);
    
    /**
     * Delete old processed events
     */
    void deleteByStatusAndCreatedAtBefore(String status, LocalDateTime cutoffDate);

    /**
     * Find retryable events based on retry attempts and delay
     */
    @Query("SELECT e FROM EventOutboxEntry e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetries " +
           "AND (e.lastRetryAt IS NULL OR e.lastRetryAt < :retryAfter) ORDER BY e.createdAt ASC")
    List<EventOutboxEntry> findRetryableEvents(
        @Param("maxRetries") int maxRetries,
        @Param("retryAfter") LocalDateTime retryAfter
    );


    /**
     * Get latest version for an aggregate
     */
    @Query("SELECT MAX(e.version) FROM EventOutboxEntry e WHERE e.aggregateId = :aggregateId")
    Optional<Long> getLatestVersionForAggregate(@Param("aggregateId") String aggregateId);
}