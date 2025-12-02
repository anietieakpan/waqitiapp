package com.waqiti.dlq.repository;

import com.waqiti.dlq.model.DLQMessage;
import com.waqiti.dlq.model.DLQMessage.DLQPriority;
import com.waqiti.dlq.model.DLQMessage.DLQStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DLQ message operations.
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Repository
public interface DLQMessageRepository extends JpaRepository<DLQMessage, UUID> {

    /**
     * Find messages ready for retry.
     */
    @Query("SELECT d FROM DLQMessage d WHERE d.status = 'READY_FOR_RETRY' " +
           "AND d.nextRetryAt <= :now " +
           "AND d.retryCount < d.maxRetries " +
           "ORDER BY d.priority DESC, d.createdAt ASC")
    List<DLQMessage> findMessagesReadyForRetry(@Param("now") LocalDateTime now);

    /**
     * Find messages by status and priority.
     */
    Page<DLQMessage> findByStatusAndPriorityOrderByCreatedAtAsc(
        DLQStatus status,
        DLQPriority priority,
        Pageable pageable
    );

    /**
     * Find messages by status.
     */
    Page<DLQMessage> findByStatusOrderByPriorityDescCreatedAtAsc(
        DLQStatus status,
        Pageable pageable
    );

    /**
     * Find messages by original topic.
     */
    List<DLQMessage> findByOriginalTopicAndStatusOrderByCreatedAtAsc(
        String originalTopic,
        DLQStatus status
    );

    /**
     * Find messages requiring manual review.
     */
    @Query("SELECT d FROM DLQMessage d WHERE d.status = 'MANUAL_REVIEW_REQUIRED' " +
           "ORDER BY d.priority DESC, d.createdAt ASC")
    Page<DLQMessage> findMessagesRequiringManualReview(Pageable pageable);

    /**
     * Find messages by idempotency key.
     */
    Optional<DLQMessage> findByIdempotencyKey(String idempotencyKey);

    /**
     * Count messages by status.
     */
    long countByStatus(DLQStatus status);

    /**
     * Count messages by status and priority.
     */
    long countByStatusAndPriority(DLQStatus status, DLQPriority priority);

    /**
     * Find old messages that have been recovered.
     */
    @Query("SELECT d FROM DLQMessage d WHERE d.status = 'RECOVERED' " +
           "AND d.recoveredAt < :cutoffDate")
    List<DLQMessage> findOldRecoveredMessages(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find messages by correlation ID.
     */
    List<DLQMessage> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);

    /**
     * Find stuck messages (retrying for too long).
     */
    @Query("SELECT d FROM DLQMessage d WHERE d.status = 'RETRYING' " +
           "AND d.updatedAt < :stuckThreshold")
    List<DLQMessage> findStuckMessages(@Param("stuckThreshold") LocalDateTime stuckThreshold);
}
