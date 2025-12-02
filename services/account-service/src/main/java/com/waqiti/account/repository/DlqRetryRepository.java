package com.waqiti.account.repository;

import com.waqiti.account.entity.DlqRetryRecord;
import com.waqiti.account.entity.DlqRetryRecord.RetryStatus;
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
 * Repository for DLQ retry queue operations
 *
 * <p>Manages messages scheduled for retry with exponential backoff.
 * Used by scheduled job to process pending retries.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Repository
public interface DlqRetryRepository extends JpaRepository<DlqRetryRecord, UUID> {

    /**
     * Find pending retries that are due for processing
     *
     * @param now Current timestamp
     * @return List of retry records ready for processing
     */
    @Query("SELECT r FROM DlqRetryRecord r WHERE r.status = 'PENDING' " +
           "AND r.nextRetryAt <= :now " +
           "ORDER BY r.nextRetryAt ASC, r.priority DESC")
    List<DlqRetryRecord> findPendingRetries(@Param("now") LocalDateTime now);

    /**
     * Find all retries for a specific topic
     *
     * @param topic Topic name
     * @param statuses Status filter
     * @return List of retry records
     */
    List<DlqRetryRecord> findByOriginalTopicAndStatusIn(String topic, List<RetryStatus> statuses);

    /**
     * Find retry record by original message coordinates
     *
     * @param topic Original topic
     * @param partition Original partition
     * @param offset Original offset
     * @return Optional retry record
     */
    Optional<DlqRetryRecord> findByOriginalTopicAndOriginalPartitionAndOriginalOffset(
        String topic, Integer partition, Long offset);

    /**
     * Find retries by correlation ID
     *
     * @param correlationId Correlation ID
     * @return List of related retry records
     */
    List<DlqRetryRecord> findByCorrelationIdOrderByCreatedAtDesc(String correlationId);

    /**
     * Find retries by handler name
     *
     * @param handlerName DLQ handler name
     * @param statuses Status filter
     * @return List of retry records
     */
    List<DlqRetryRecord> findByHandlerNameAndStatusIn(String handlerName, List<RetryStatus> statuses);

    /**
     * Count pending retries
     *
     * @return Count of pending retries
     */
    @Query("SELECT COUNT(r) FROM DlqRetryRecord r WHERE r.status = 'PENDING'")
    long countPendingRetries();

    /**
     * Count retries by status
     *
     * @param status Retry status
     * @return Count
     */
    long countByStatus(RetryStatus status);

    /**
     * Count retries by topic and status
     *
     * @param topic Topic name
     * @param status Retry status
     * @return Count
     */
    long countByOriginalTopicAndStatus(String topic, RetryStatus status);

    /**
     * Delete completed/failed retries older than retention period
     *
     * @param olderThan Cutoff date
     * @param statuses Terminal statuses (SUCCESS, FAILED, CANCELLED)
     * @return Number of deleted records
     */
    @Modifying
    @Query("DELETE FROM DlqRetryRecord r WHERE r.updatedAt < :olderThan " +
           "AND r.status IN :statuses")
    int deleteByUpdatedAtBeforeAndStatusIn(
        @Param("olderThan") LocalDateTime olderThan,
        @Param("statuses") List<RetryStatus> statuses);

    /**
     * Find retries that have exceeded max attempts
     *
     * @return List of exhausted retry records
     */
    @Query("SELECT r FROM DlqRetryRecord r WHERE r.retryAttempt >= r.maxRetryAttempts " +
           "AND r.status = 'PENDING'")
    List<DlqRetryRecord> findExhaustedRetries();

    /**
     * Update retry status in bulk
     *
     * @param ids Record IDs
     * @param status New status
     * @return Number of updated records
     */
    @Modifying
    @Query("UPDATE DlqRetryRecord r SET r.status = :status, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id IN :ids")
    int updateStatusBulk(@Param("ids") List<UUID> ids, @Param("status") RetryStatus status);

    /**
     * Find oldest pending retry per topic (for monitoring)
     *
     * @return List of oldest retries grouped by topic
     */
    @Query("SELECT r FROM DlqRetryRecord r WHERE r.createdAt IN " +
           "(SELECT MIN(r2.createdAt) FROM DlqRetryRecord r2 " +
           "WHERE r2.status = 'PENDING' GROUP BY r2.originalTopic) " +
           "AND r.status = 'PENDING'")
    List<DlqRetryRecord> findOldestPendingRetryPerTopic();
}
