package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.DlqRetryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for DLQ Retry History operations
 */
@Repository
public interface DlqRetryHistoryRepository extends JpaRepository<DlqRetryHistory, UUID> {

    /**
     * Find all retry history for a DLQ message
     */
    List<DlqRetryHistory> findByDlqMessageIdOrderByRetryTimestampDesc(UUID dlqMessageId);

    /**
     * Find retry history by status
     */
    List<DlqRetryHistory> findByRetryStatusOrderByRetryTimestampDesc(DlqRetryHistory.RetryStatus retryStatus);

    /**
     * Count retries for a message
     */
    long countByDlqMessageId(UUID dlqMessageId);

    /**
     * Find recent failed retries for pattern analysis
     */
    @Query("SELECT r FROM DlqRetryHistory r WHERE r.retryStatus = 'FAILURE' " +
           "AND r.retryTimestamp >= :since " +
           "ORDER BY r.retryTimestamp DESC")
    List<DlqRetryHistory> findRecentFailures(@Param("since") LocalDateTime since);

    /**
     * Delete old retry history
     */
    long deleteByRetryTimestampBefore(LocalDateTime before);
}
