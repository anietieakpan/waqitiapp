package com.waqiti.common.dlq;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for DLQ message persistence and querying
 */
@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessage, String> {

    /**
     * Find messages by topic pattern
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.originalTopic LIKE %:pattern%")
    Page<DlqMessage> findByTopicPattern(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Find messages by error pattern
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.errorMessage LIKE %:pattern% OR d.errorType LIKE %:pattern%")
    Page<DlqMessage> findByErrorPattern(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Find messages by status (using escalation level as proxy for status)
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.escalationLevel = :status")
    Page<DlqMessage> findByStatus(@Param("status") DlqMessage.EscalationLevel status, Pageable pageable);

    /**
     * Find messages within a timestamp range
     */
    @Query("SELECT d FROM DlqMessage d WHERE d.failureTimestamp BETWEEN :start AND :end")
    Page<DlqMessage> findByTimestampRange(@Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    /**
     * Count messages by status
     */
    @Query("SELECT d.escalationLevel as status, COUNT(d) as count FROM DlqMessage d GROUP BY d.escalationLevel")
    Map<DlqMessage.EscalationLevel, Long> countByStatus();

    /**
     * Count messages by topic
     */
    @Query("SELECT d.originalTopic as topic, COUNT(d) as count FROM DlqMessage d GROUP BY d.originalTopic")
    Map<String, Long> countByTopic();

    /**
     * Find oldest message
     */
    @Query("SELECT MIN(d.failureTimestamp) FROM DlqMessage d")
    Optional<Instant> findOldestMessage();

    /**
     * Calculate average reprocessing attempts
     */
    @Query("SELECT AVG(d.retryCount) FROM DlqMessage d")
    Double averageReprocessingAttempts();

    /**
     * Calculate reprocessing success rate
     */
    @Query("SELECT CAST(SUM(CASE WHEN d.escalationLevel = 'NONE' THEN 1 ELSE 0 END) AS double) / COUNT(d) FROM DlqMessage d")
    Double calculateSuccessRate();
}
