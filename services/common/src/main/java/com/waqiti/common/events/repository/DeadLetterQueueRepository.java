package com.waqiti.common.events.repository;

import com.waqiti.common.events.model.DeadLetterQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for dead letter queue management
 * Handles storage of events that could not be processed
 */
@Repository
public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueueEntry, UUID> {
    
    /**
     * Find entries by topic
     */
    List<DeadLetterQueueEntry> findByTopic(String topic);
    
    /**
     * Find entries by error type
     */
    List<DeadLetterQueueEntry> findByErrorType(String errorType);
    
    /**
     * Find entries ready for retry
     */
    @Query("SELECT d FROM DeadLetterQueueEntry d WHERE d.status = 'PENDING_RETRY' AND d.nextRetryAt <= :now")
    List<DeadLetterQueueEntry> findEntriesReadyForRetry(@Param("now") LocalDateTime now);
    
    /**
     * Find entries by date range
     */
    List<DeadLetterQueueEntry> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * Count entries by status
     */
    long countByStatus(String status);
    
    /**
     * Count entries by topic and status
     */
    long countByTopicAndStatus(String topic, String status);
    
    /**
     * Delete old entries
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
    
    /**
     * Find unresolved entries
     */
    @Query("SELECT d FROM DeadLetterQueueEntry d WHERE d.status != 'RESOLVED' AND d.status != 'DISCARDED'")
    List<DeadLetterQueueEntry> findUnresolvedEntries();
    
    /**
     * Find oldest unprocessed entry
     */
    @Query("SELECT MIN(d.createdAt) FROM DeadLetterQueueEntry d WHERE d.status = 'UNPROCESSED'")
    LocalDateTime findOldestUnprocessedEntryTime();
    
    /**
     * Find entries for metrics aggregation
     */
    @Query("SELECT d.topic as topic, COUNT(d) as count FROM DeadLetterQueueEntry d GROUP BY d.topic")
    List<Object[]> countEntriesByTopic();
    
    /**
     * Find entries by error type for metrics
     */
    @Query("SELECT d.errorType as errorType, COUNT(d) as count FROM DeadLetterQueueEntry d GROUP BY d.errorType")
    List<Object[]> countEntriesByErrorType();
    
    /**
     * Count entries requiring manual review
     */
    @Query("SELECT COUNT(d) FROM DeadLetterQueueEntry d WHERE d.status = 'MANUAL_REVIEW'")
    long countManualReviewEntries();
    
    /**
     * Find replayable events for bulk replay
     */
    @Query("SELECT d FROM DeadLetterQueueEntry d WHERE d.eventType = :eventType AND d.createdAt >= :since AND (d.status = 'UNPROCESSED' OR d.status = 'MANUAL_REVIEW')")
    List<DeadLetterQueueEntry> findReplayableEvents(@Param("eventType") String eventType, @Param("since") LocalDateTime since);
    
    /**
     * Delete old entries for cleanup
     */
    @Modifying
    @Query("DELETE FROM DeadLetterQueueEntry d WHERE d.createdAt < :cutoffDate")
    int deleteOldEntries(@Param("cutoffDate") LocalDateTime cutoffDate);
}