package com.waqiti.account.repository;

import com.waqiti.account.entity.ManualReviewRecord;
import com.waqiti.account.entity.ManualReviewRecord.ReviewPriority;
import com.waqiti.account.entity.ManualReviewRecord.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for manual review queue operations
 *
 * <p>Manages messages requiring human intervention with SLA tracking.
 * Supports priority-based assignment and escalation workflows.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Repository
public interface ManualReviewRepository extends JpaRepository<ManualReviewRecord, UUID> {

    /**
     * Find pending reviews ordered by priority and SLA
     *
     * @return List of pending reviews
     */
    @Query("SELECT r FROM ManualReviewRecord r WHERE r.status = 'PENDING' " +
           "ORDER BY r.priority ASC, r.slaDueAt ASC")
    List<ManualReviewRecord> findPendingReviewsByPriority();

    /**
     * Find reviews by status
     *
     * @param statuses Status filter
     * @return List of matching reviews
     */
    List<ManualReviewRecord> findByStatusInOrderByPriorityAscCreatedAtAsc(List<ReviewStatus> statuses);

    /**
     * Find reviews assigned to specific user
     *
     * @param assignedTo User identifier
     * @param statuses Status filter
     * @return List of assigned reviews
     */
    List<ManualReviewRecord> findByAssignedToAndStatusIn(String assignedTo, List<ReviewStatus> statuses);

    /**
     * Find reviews by priority
     *
     * @param priority Priority level
     * @param statuses Status filter
     * @return List of reviews
     */
    List<ManualReviewRecord> findByPriorityAndStatusIn(ReviewPriority priority, List<ReviewStatus> statuses);

    /**
     * Find reviews by topic
     *
     * @param topic Original topic name
     * @param statuses Status filter
     * @return List of reviews
     */
    List<ManualReviewRecord> findByOriginalTopicAndStatusIn(String topic, List<ReviewStatus> statuses);

    /**
     * Find reviews by correlation ID
     *
     * @param correlationId Correlation ID
     * @return List of related reviews
     */
    List<ManualReviewRecord> findByCorrelationIdOrderByCreatedAtDesc(String correlationId);

    /**
     * Find SLA breached reviews
     *
     * @param now Current timestamp
     * @return List of breached reviews
     */
    @Query("SELECT r FROM ManualReviewRecord r WHERE r.slaDueAt < :now " +
           "AND r.status IN ('PENDING', 'IN_REVIEW') " +
           "ORDER BY r.priority ASC, r.slaDueAt ASC")
    List<ManualReviewRecord> findSlaBreachedReviews(@Param("now") LocalDateTime now);

    /**
     * Find unassigned reviews
     *
     * @return List of unassigned pending reviews
     */
    @Query("SELECT r FROM ManualReviewRecord r WHERE r.assignedTo IS NULL " +
           "AND r.status = 'PENDING' ORDER BY r.priority ASC, r.createdAt ASC")
    List<ManualReviewRecord> findUnassignedReviews();

    /**
     * Find review by original message coordinates
     *
     * @param topic Original topic
     * @param partition Original partition
     * @param offset Original offset
     * @return Optional review record
     */
    Optional<ManualReviewRecord> findByOriginalTopicAndOriginalPartitionAndOriginalOffset(
        String topic, Integer partition, Long offset);

    /**
     * Count reviews by status
     *
     * @param status Review status
     * @return Count
     */
    long countByStatus(ReviewStatus status);

    /**
     * Count reviews by priority
     *
     * @param priority Priority level
     * @return Count
     */
    long countByPriority(ReviewPriority priority);

    /**
     * Count SLA breached reviews
     *
     * @param now Current timestamp
     * @return Count of breached reviews
     */
    @Query("SELECT COUNT(r) FROM ManualReviewRecord r WHERE r.slaDueAt < :now " +
           "AND r.status IN ('PENDING', 'IN_REVIEW')")
    long countSlaBreachedReviews(@Param("now") LocalDateTime now);

    /**
     * Count reviews by topic and status
     *
     * @param topic Topic name
     * @param status Review status
     * @return Count
     */
    long countByOriginalTopicAndStatus(String topic, ReviewStatus status);

    /**
     * Mark SLA as breached for overdue reviews
     *
     * @param now Current timestamp
     * @return Number of updated records
     */
    @Modifying
    @Query("UPDATE ManualReviewRecord r SET r.slaBreached = true, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.slaDueAt < :now AND r.status IN ('PENDING', 'IN_REVIEW') " +
           "AND r.slaBreached = false")
    int markSlaBreached(@Param("now") LocalDateTime now);

    /**
     * Find oldest unresolved review per topic (for monitoring)
     *
     * @return List of oldest reviews grouped by topic
     */
    @Query("SELECT r FROM ManualReviewRecord r WHERE r.createdAt IN " +
           "(SELECT MIN(r2.createdAt) FROM ManualReviewRecord r2 " +
           "WHERE r2.status IN ('PENDING', 'IN_REVIEW', 'ESCALATED') " +
           "GROUP BY r2.originalTopic) " +
           "AND r.status IN ('PENDING', 'IN_REVIEW', 'ESCALATED')")
    List<ManualReviewRecord> findOldestUnresolvedReviewPerTopic();

    /**
     * Find critical reviews requiring immediate attention
     *
     * @return List of critical unassigned reviews
     */
    @Query("SELECT r FROM ManualReviewRecord r WHERE r.priority = 'CRITICAL' " +
           "AND r.status = 'PENDING' AND r.assignedTo IS NULL " +
           "ORDER BY r.createdAt ASC")
    List<ManualReviewRecord> findCriticalUnassignedReviews();

    /**
     * Delete resolved reviews older than retention period
     *
     * @param olderThan Cutoff date
     * @param statuses Terminal statuses (RESOLVED, DISMISSED)
     * @return Number of deleted records
     */
    @Modifying
    @Query("DELETE FROM ManualReviewRecord r WHERE r.updatedAt < :olderThan " +
           "AND r.status IN :statuses")
    int deleteByUpdatedAtBeforeAndStatusIn(
        @Param("olderThan") LocalDateTime olderThan,
        @Param("statuses") List<ReviewStatus> statuses);
}
