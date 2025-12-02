package com.waqiti.business.repository;

import com.waqiti.business.domain.DlqMessage;
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
 * Repository for DLQ Message persistence and retrieval
 *
 * Provides specialized queries for DLQ message management,
 * retry scheduling, and manual intervention workflows.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessage, UUID> {

    /**
     * Find all messages for a specific consumer
     */
    Page<DlqMessage> findByConsumerName(String consumerName, Pageable pageable);

    /**
     * Find messages by status
     */
    Page<DlqMessage> findByStatus(DlqMessage.DlqStatus status, Pageable pageable);

    /**
     * Find messages eligible for retry (scheduled time has passed)
     */
    @Query("SELECT d FROM DlqMessage d WHERE " +
           "d.status IN ('RETRY_SCHEDULED', 'PENDING') " +
           "AND d.retryCount < d.maxRetries " +
           "AND (d.retryAfter IS NULL OR d.retryAfter <= :now)")
    List<DlqMessage> findMessagesEligibleForRetry(@Param("now") LocalDateTime now);

    /**
     * Find messages requiring manual intervention
     */
    @Query("SELECT d FROM DlqMessage d WHERE " +
           "d.status IN ('MANUAL_INTERVENTION_REQUIRED', 'MAX_RETRIES_EXCEEDED', 'PERMANENT_FAILURE') " +
           "ORDER BY d.createdAt DESC")
    Page<DlqMessage> findMessagesRequiringIntervention(Pageable pageable);

    /**
     * Find messages by consumer and status
     */
    List<DlqMessage> findByConsumerNameAndStatus(String consumerName, DlqMessage.DlqStatus status);

    /**
     * Count messages by status
     */
    long countByStatus(DlqMessage.DlqStatus status);

    /**
     * Count messages by consumer
     */
    long countByConsumerName(String consumerName);

    /**
     * Find oldest unresolved messages
     */
    @Query("SELECT d FROM DlqMessage d WHERE " +
           "d.status NOT IN ('RECOVERED', 'ARCHIVED') " +
           "ORDER BY d.createdAt ASC")
    Page<DlqMessage> findOldestUnresolved(Pageable pageable);

    /**
     * Find messages by original topic
     */
    List<DlqMessage> findByOriginalTopic(String originalTopic);

    /**
     * Find messages by message key (for deduplication)
     */
    Optional<DlqMessage> findFirstByMessageKeyOrderByCreatedAtDesc(String messageKey);

    /**
     * Find messages created within time range
     */
    @Query("SELECT d FROM DlqMessage d WHERE " +
           "d.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY d.createdAt DESC")
    List<DlqMessage> findByCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count messages that exceeded max retries in the last 24 hours
     */
    @Query("SELECT COUNT(d) FROM DlqMessage d WHERE " +
           "d.status = 'MAX_RETRIES_EXCEEDED' " +
           "AND d.createdAt >= :since")
    long countMaxRetriesExceededSince(@Param("since") LocalDateTime since);

    /**
     * Get retry statistics by consumer
     */
    @Query("SELECT d.consumerName as consumer, " +
           "COUNT(d) as total, " +
           "SUM(CASE WHEN d.status = 'RECOVERED' THEN 1 ELSE 0 END) as recovered, " +
           "SUM(CASE WHEN d.status = 'MANUAL_INTERVENTION_REQUIRED' THEN 1 ELSE 0 END) as needsIntervention, " +
           "AVG(d.retryCount) as avgRetries " +
           "FROM DlqMessage d " +
           "WHERE d.createdAt >= :since " +
           "GROUP BY d.consumerName")
    List<Object[]> getRetryStatistics(@Param("since") LocalDateTime since);
}
