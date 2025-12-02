package com.waqiti.common.messaging.deadletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Dead Letter Queue related imports
import com.waqiti.common.messaging.deadletter.DeadLetterRecord;
import com.waqiti.common.messaging.deadletter.DeadLetterStatus;
import com.waqiti.common.messaging.deadletter.DlqHourlyStats;

/**
 * Dead Letter Queue Repository
 * 
 * Provides data access for dead letter queue records with
 * comprehensive querying and maintenance operations.
 */
@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterRecord, UUID> {
    
    /**
     * Find dead letter messages by original topic
     */
    @Query("SELECT d FROM DeadLetterRecord d WHERE d.originalTopic = :topic ORDER BY d.createdAt DESC")
    List<DeadLetterRecord> findByOriginalTopicOrderByCreatedAtDesc(@Param("topic") String topic, @Param("limit") int limit);
    
    /**
     * Find dead letter message by message ID
     */
    Optional<DeadLetterRecord> findByMessageId(String messageId);
    
    /**
     * Count total messages by topic
     */
    long countByOriginalTopic(String originalTopic);
    
    /**
     * Count poison messages by topic
     */
    long countByOriginalTopicAndPoisonMessage(String originalTopic, boolean poisonMessage);
    
    /**
     * Count messages by topic and status
     */
    long countByOriginalTopicAndStatus(String originalTopic, DeadLetterStatus status);
    
    /**
     * Find recent failure reasons for a topic
     */
    @Query("SELECT DISTINCT d.failureReason FROM DeadLetterRecord d " +
           "WHERE d.originalTopic = :topic " +
           "AND d.createdAt >= :since " +
           "ORDER BY d.createdAt DESC")
    List<String> findRecentFailureReasons(@Param("topic") String topic, @Param("since") LocalDateTime since);
    
    default List<String> findRecentFailureReasons(String topic, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return findRecentFailureReasons(topic, since);
    }
    
    /**
     * Get hourly statistics for DLQ messages
     */
    @Query("SELECT " +
           "EXTRACT(HOUR FROM d.createdAt) as hour, " +
           "COUNT(d) as messageCount, " +
           "COUNT(CASE WHEN d.poisonMessage = true THEN 1 END) as poisonCount " +
           "FROM DeadLetterRecord d " +
           "WHERE d.originalTopic = :topic " +
           "AND d.createdAt >= :since " +
           "GROUP BY EXTRACT(HOUR FROM d.createdAt) " +
           "ORDER BY hour")
    List<Object[]> getHourlyStatisticsRaw(@Param("topic") String topic, @Param("since") LocalDateTime since);
    
    default List<DlqHourlyStats> getHourlyStatistics(String topic, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Object[]> rawStats = getHourlyStatisticsRaw(topic, since);
        
        return rawStats.stream()
            .map(row -> DlqHourlyStats.builder()
                .hour((Integer) row[0])
                .messageCount(((Number) row[1]).longValue())
                .poisonCount(((Number) row[2]).longValue())
                .build())
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Find messages pending retry
     */
    @Query("SELECT d FROM DeadLetterRecord d " +
           "WHERE d.status = 'PENDING' " +
           "AND d.retryCount < :maxRetries " +
           "AND d.nextRetryAt <= :now " +
           "ORDER BY d.nextRetryAt ASC")
    List<DeadLetterRecord> findMessagesReadyForRetry(@Param("maxRetries") int maxRetries, @Param("now") LocalDateTime now);
    
    /**
     * Find old poison messages for cleanup
     */
    @Query("SELECT d FROM DeadLetterRecord d " +
           "WHERE d.poisonMessage = true " +
           "AND d.createdAt < :cutoffDate " +
           "ORDER BY d.createdAt ASC")
    List<DeadLetterRecord> findOldPoisonMessages(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Find messages by status
     */
    List<DeadLetterRecord> findByStatusOrderByCreatedAtDesc(DeadLetterStatus status);
    
    /**
     * Find messages by priority
     */
    @Query("SELECT d FROM DeadLetterRecord d " +
           "WHERE JSON_EXTRACT(d.messageData, '$.priority') = :priority " +
           "ORDER BY d.createdAt DESC")
    List<DeadLetterRecord> findByPriority(@Param("priority") String priority);
    
    /**
     * Find financial messages in DLQ
     */
    @Query("SELECT d FROM DeadLetterRecord d " +
           "WHERE JSON_EXTRACT(d.messageData, '$.financialMessage') = true " +
           "ORDER BY d.createdAt DESC")
    List<DeadLetterRecord> findFinancialMessagesInDlq();
    
    /**
     * Delete old messages for maintenance
     */
    @Modifying
    @Query("DELETE FROM DeadLetterRecord d WHERE d.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Find messages requiring manual intervention
     */
    @Query("SELECT d FROM DeadLetterRecord d " +
           "WHERE JSON_EXTRACT(d.messageData, '$.manualInterventionRequired') = true " +
           "OR d.poisonMessage = true " +
           "OR d.retryCount >= :maxRetries " +
           "ORDER BY d.createdAt DESC")
    List<DeadLetterRecord> findMessagesRequiringManualIntervention(@Param("maxRetries") int maxRetries);
    
    /**
     * Get DLQ summary statistics
     */
    @Query("SELECT " +
           "COUNT(d) as totalMessages, " +
           "COUNT(CASE WHEN d.status = 'PENDING' THEN 1 END) as pendingMessages, " +
           "COUNT(CASE WHEN d.poisonMessage = true THEN 1 END) as poisonMessages, " +
           "COUNT(CASE WHEN d.status = 'REPROCESSED' THEN 1 END) as reprocessedMessages, " +
           "AVG(d.retryCount) as averageRetryCount " +
           "FROM DeadLetterRecord d " +
           "WHERE d.originalTopic = :topic")
    Object[] getDlqSummaryStatistics(@Param("topic") String topic);
    
    /**
     * Find messages by error type
     */
    List<DeadLetterRecord> findByErrorTypeOrderByCreatedAtDesc(String errorType);
    
    /**
     * Find messages created within time range
     */
    List<DeadLetterRecord> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Update message status
     */
    @Modifying
    @Query("UPDATE DeadLetterRecord d SET d.status = :status, d.updatedAt = :now WHERE d.messageId = :messageId")
    int updateMessageStatus(@Param("messageId") String messageId, @Param("status") DeadLetterStatus status, @Param("now") LocalDateTime now);
}