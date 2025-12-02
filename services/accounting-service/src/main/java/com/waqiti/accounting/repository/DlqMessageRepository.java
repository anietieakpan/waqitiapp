package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.DlqMessage;
import com.waqiti.accounting.domain.DlqStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DLQ Message operations
 */
@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessage, UUID> {

    /**
     * Find message by message ID
     */
    Optional<DlqMessage> findByMessageId(String messageId);

    /**
     * Find all messages by status
     */
    List<DlqMessage> findByStatus(DlqStatus status);

    /**
     * Find messages by status with pagination
     */
    Page<DlqMessage> findByStatus(DlqStatus status, Pageable pageable);

    /**
     * Find messages by topic and status
     */
    List<DlqMessage> findByTopicAndStatus(String topic, DlqStatus status);

    /**
     * Find messages ready for retry (with pessimistic lock to prevent concurrent processing)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'PENDING' " +
           "AND d.retryCount < d.maxRetryAttempts " +
           "AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now) " +
           "ORDER BY d.nextRetryAt ASC")
    List<DlqMessage> findMessagesReadyForRetry(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Find messages requiring manual review
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'MANUAL_REVIEW' " +
           "ORDER BY d.firstFailureAt ASC")
    List<DlqMessage> findMessagesRequiringManualReview(Pageable pageable);

    /**
     * Count messages by status
     */
    long countByStatus(DlqStatus status);

    /**
     * Count messages by topic in time window (for alerting)
     */
    @Query("SELECT COUNT(d) FROM DlqMessage d WHERE d.topic = :topic " +
           "AND d.firstFailureAt >= :since")
    long countByTopicSince(@Param("topic") String topic, @Param("since") LocalDateTime since);

    /**
     * Find messages by error class for pattern analysis
     */
    List<DlqMessage> findByErrorClass(String errorClass);

    /**
     * Find old resolved messages for cleanup
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'RESOLVED' " +
           "AND d.resolvedAt < :before")
    List<DlqMessage> findOldResolvedMessages(@Param("before") LocalDateTime before, Pageable pageable);

    /**
     * Delete old resolved messages
     */
    long deleteByStatusAndResolvedAtBefore(DlqStatus status, LocalDateTime before);

    /**
     * Get DLQ statistics
     */
    @Query("SELECT d.status as status, d.topic as topic, COUNT(d) as count " +
           "FROM DlqMessage d " +
           "WHERE d.createdAt >= :since " +
           "GROUP BY d.status, d.topic")
    List<DlqStatistic> getDlqStatistics(@Param("since") LocalDateTime since);

    /**
     * Projection for statistics
     */
    interface DlqStatistic {
        DlqStatus getStatus();
        String getTopic();
        Long getCount();
    }
}
