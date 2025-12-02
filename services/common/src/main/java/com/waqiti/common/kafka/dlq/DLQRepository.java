package com.waqiti.common.kafka.dlq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * DLQ Message Repository for persistence and replay
 *
 * This repository provides:
 * - Persistent storage of DLQ messages
 * - Query capabilities for monitoring and reporting
 * - Replay functionality for manual intervention
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-09
 */
@Repository
public interface DLQRepository extends JpaRepository<UniversalDLQHandler.DLQMessage, String> {

    /**
     * Find all messages for a specific topic in parking lot
     */
    @Query("SELECT d FROM DLQMessage d WHERE d.originalTopic = :topic " +
           "AND d.attemptNumber > :maxAttempts ORDER BY d.failureTimestamp DESC")
    List<UniversalDLQHandler.DLQMessage> findParkingLotMessages(
        @Param("topic") String topic,
        @Param("maxAttempts") int maxAttempts
    );

    /**
     * Find all messages by failure category
     */
    List<UniversalDLQHandler.DLQMessage> findByFailureCategory(
        UniversalDLQHandler.FailureCategory category
    );

    /**
     * Find messages that need retry (based on time window)
     */
    @Query("SELECT d FROM DLQMessage d WHERE d.failureTimestamp < :retryAfter " +
           "AND d.attemptNumber <= :maxAttempts ORDER BY d.failureTimestamp ASC")
    List<UniversalDLQHandler.DLQMessage> findMessagesForRetry(
        @Param("retryAfter") Instant retryAfter,
        @Param("maxAttempts") int maxAttempts
    );

    /**
     * Count messages in parking lot by topic
     */
    @Query("SELECT COUNT(d) FROM DLQMessage d WHERE d.originalTopic = :topic " +
           "AND d.attemptNumber > :maxAttempts")
    long countParkingLotMessagesByTopic(
        @Param("topic") String topic,
        @Param("maxAttempts") int maxAttempts
    );

    /**
     * Find recent failures (last 24 hours) for monitoring
     */
    @Query("SELECT d FROM DLQMessage d WHERE d.failureTimestamp >= :since " +
           "ORDER BY d.failureTimestamp DESC")
    List<UniversalDLQHandler.DLQMessage> findRecentFailures(@Param("since") Instant since);

    /**
     * Find messages by original topic and offset range (for targeted replay)
     */
    @Query("SELECT d FROM DLQMessage d WHERE d.originalTopic = :topic " +
           "AND d.originalOffset >= :startOffset AND d.originalOffset <= :endOffset " +
           "ORDER BY d.originalOffset ASC")
    List<UniversalDLQHandler.DLQMessage> findByTopicAndOffsetRange(
        @Param("topic") String topic,
        @Param("startOffset") long startOffset,
        @Param("endOffset") long endOffset
    );
}
