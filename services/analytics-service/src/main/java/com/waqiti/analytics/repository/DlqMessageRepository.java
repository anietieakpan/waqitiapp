package com.waqiti.analytics.repository;

import com.waqiti.analytics.entity.DlqMessage;
import com.waqiti.analytics.entity.DlqMessage.DlqStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DLQ Message persistence and queries
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessage, UUID> {

    /**
     * Find messages by status
     */
    List<DlqMessage> findByStatusOrderByReceivedAtAsc(DlqStatus status);

    /**
     * Find messages by original topic and status
     */
    List<DlqMessage> findByOriginalTopicAndStatus(String originalTopic, DlqStatus status);

    /**
     * Find message by correlation ID
     */
    Optional<DlqMessage> findByCorrelationId(String correlationId);

    /**
     * Find messages eligible for retry
     * (status = PENDING_REVIEW and retryCount < maxRetryAttempts)
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'PENDING_REVIEW' " +
           "AND d.retryCount < d.maxRetryAttempts " +
           "ORDER BY d.severity DESC, d.receivedAt ASC")
    List<DlqMessage> findEligibleForRetry();

    /**
     * Find stale messages (pending for too long)
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'PENDING_REVIEW' " +
           "AND d.receivedAt < :threshold")
    List<DlqMessage> findStalePendingMessages(@Param("threshold") LocalDateTime threshold);

    /**
     * Count messages by status
     */
    long countByStatus(DlqStatus status);

    /**
     * Count messages by original topic
     */
    long countByOriginalTopic(String originalTopic);

    /**
     * Find failed messages that need alerting
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'FAILED' AND d.alerted = false")
    List<DlqMessage> findFailedMessagesNotAlerted();

    /**
     * Get status statistics
     */
    @Query("SELECT d.status, COUNT(d) FROM DlqMessage d GROUP BY d.status")
    List<Object[]> getStatusStatistics();

    /**
     * Find messages by assigned reviewer
     */
    List<DlqMessage> findByAssignedToAndStatus(String assignedTo, DlqStatus status);

    /**
     * Find recent failures (last 24 hours) by topic
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.originalTopic = :topic " +
           "AND d.receivedAt > :since ORDER BY d.receivedAt DESC")
    List<DlqMessage> findRecentFailuresByTopic(
        @Param("topic") String topic,
        @Param("since") LocalDateTime since
    );
}
