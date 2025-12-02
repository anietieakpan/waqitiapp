package com.waqiti.support.repository;

import com.waqiti.support.domain.DlqMessage;
import com.waqiti.support.domain.DlqMessage.DlqStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for DLQ message persistence and querying.
 */
@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessage, String> {

    /**
     * Find all messages pending review.
     */
    List<DlqMessage> findByStatusOrderByReceivedAtDesc(DlqStatus status);

    /**
     * Find messages scheduled for retry that are due.
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'RETRY_SCHEDULED' " +
           "AND d.nextRetryAt <= :now ORDER BY d.nextRetryAt ASC")
    List<DlqMessage> findDueForRetry(@Param("now") Instant now);

    /**
     * Find messages by original topic.
     */
    List<DlqMessage> findByOriginalTopicOrderByReceivedAtDesc(String originalTopic);

    /**
     * Find messages by status and topic.
     */
    List<DlqMessage> findByStatusAndOriginalTopic(DlqStatus status, String topic);

    /**
     * Count messages by status.
     */
    long countByStatus(DlqStatus status);

    /**
     * Find messages that haven't had an alert sent.
     */
    List<DlqMessage> findByAlertSentFalseAndStatusNotIn(List<DlqStatus> excludedStatuses);

    /**
     * Find old pending messages (potential stuck messages).
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'PENDING_REVIEW' " +
           "AND d.receivedAt < :cutoffTime")
    List<DlqMessage> findStalePendingMessages(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Find permanent failures for reporting.
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.status = 'PERMANENT_FAILURE' " +
           "AND d.resolvedAt >= :since ORDER BY d.resolvedAt DESC")
    List<DlqMessage> findRecentPermanentFailures(@Param("since") Instant since);
}
