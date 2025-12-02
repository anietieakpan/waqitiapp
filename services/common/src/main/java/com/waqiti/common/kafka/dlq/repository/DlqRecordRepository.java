package com.waqiti.common.kafka.dlq.repository;

import com.waqiti.common.kafka.dlq.DlqEventType;
import com.waqiti.common.kafka.dlq.DlqStatus;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DLQ Record Entities
 *
 * Provides comprehensive query capabilities for:
 * - Message retrieval and filtering
 * - Statistics and monitoring
 * - Batch operations
 * - Cleanup and archival
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Repository
public interface DlqRecordRepository extends JpaRepository<DlqRecordEntity, UUID> {

    // ========== BASIC QUERIES ==========

    Optional<DlqRecordEntity> findByMessageId(String messageId);

    List<DlqRecordEntity> findByStatus(DlqStatus status);

    List<DlqRecordEntity> findByEventType(DlqEventType eventType);

    List<DlqRecordEntity> findByServiceName(String serviceName);

    // ========== COUNTING QUERIES ==========

    long countByStatus(DlqStatus status);

    long countByEventType(DlqEventType eventType);

    long countByServiceName(String serviceName);

    @Query("SELECT COUNT(d) FROM DlqRecordEntity d WHERE d.retryCount >= :threshold AND d.status != 'REPROCESSED'")
    long countCriticalFailures(@Param("threshold") int threshold);

    // ========== PENDING MESSAGE QUERIES ==========

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.status = :status AND d.eventType = :eventType " +
           "ORDER BY d.createdAt ASC")
    Page<DlqRecordEntity> findPendingByEventType(
            @Param("status") DlqStatus status,
            @Param("eventType") DlqEventType eventType,
            Pageable pageable
    );

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.status = 'PENDING' " +
           "AND (d.nextRetryTime IS NULL OR d.nextRetryTime <= :now) " +
           "ORDER BY d.createdAt ASC")
    List<DlqRecordEntity> findPendingReadyForRetry(@Param("now") Instant now, Pageable pageable);

    // ========== STATISTICS QUERIES ==========

    @Query("SELECT d.eventType as eventType, COUNT(d) as count FROM DlqRecordEntity d " +
           "GROUP BY d.eventType")
    List<EventTypeCount> countByEventType();

    @Query("SELECT d.serviceName as serviceName, COUNT(d) as count FROM DlqRecordEntity d " +
           "WHERE d.status = :status GROUP BY d.serviceName")
    List<ServiceNameCount> countByServiceNameAndStatus(@Param("status") DlqStatus status);

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.status = 'PENDING' " +
           "ORDER BY d.createdAt ASC")
    Optional<DlqRecordEntity> findOldestPending();

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.status = 'PARKED' " +
           "ORDER BY d.parkedAt ASC")
    Optional<DlqRecordEntity> findOldestParked();

    // ========== TIME-BASED QUERIES ==========

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.createdAt >= :since " +
           "ORDER BY d.createdAt DESC")
    List<DlqRecordEntity> findRecentFailures(@Param("since") LocalDateTime since);

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.reprocessedAt >= :since")
    List<DlqRecordEntity> findRecentlyReprocessed(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(d) FROM DlqRecordEntity d WHERE d.status = 'REPROCESSED' " +
           "AND d.reprocessedAt >= :since")
    long countFiledInLast24Hours(@Param("since") LocalDateTime since);

    // ========== PARKED MESSAGE QUERIES ==========

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.status = 'PARKED' " +
           "AND d.parkedAt < :cutoffDate")
    List<DlqRecordEntity> findOldParkedRecords(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.status = 'PARKED' " +
           "AND d.eventType IN :criticalEventTypes " +
           "ORDER BY d.parkedAt ASC")
    List<DlqRecordEntity> findCriticalParkedMessages(@Param("criticalEventTypes") List<DlqEventType> criticalEventTypes);

    // ========== CLEANUP QUERIES ==========

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.status = 'REPROCESSED' " +
           "AND d.reprocessedAt < :cutoffDate")
    List<DlqRecordEntity> findOldReprocessedRecords(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM DlqRecordEntity d WHERE d.status = 'REPROCESSED' " +
           "AND d.reprocessedAt < :cutoffDate")
    int deleteOldReprocessedRecords(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM DlqRecordEntity d WHERE d.createdAt < :cutoffDate " +
           "AND d.status IN ('MANUALLY_RESOLVED', 'DISCARDED')")
    int deleteOldResolvedRecords(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== TOPIC & OFFSET QUERIES ==========

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.topic = :topic " +
           "AND d.partition = :partition AND d.offset >= :startOffset AND d.offset <= :endOffset " +
           "ORDER BY d.offset ASC")
    List<DlqRecordEntity> findByTopicPartitionAndOffsetRange(
            @Param("topic") String topic,
            @Param("partition") Integer partition,
            @Param("startOffset") Long startOffset,
            @Param("endOffset") Long endOffset
    );

    @Query("SELECT d FROM DlqRecordEntity d WHERE d.topic = :topic " +
           "AND d.status = :status ORDER BY d.createdAt DESC")
    Page<DlqRecordEntity> findByTopicAndStatus(
            @Param("topic") String topic,
            @Param("status") DlqStatus status,
            Pageable pageable
    );

    // ========== AVERAGE CALCULATION QUERIES ==========

    @Query("SELECT AVG(EXTRACT(EPOCH FROM (d.reprocessedAt - d.createdAt))) " +
           "FROM DlqRecordEntity d WHERE d.status = 'REPROCESSED' " +
           "AND d.reprocessedAt IS NOT NULL")
    Double calculateAverageTimeToReprocess();

    @Query("SELECT AVG(d.retryCount) FROM DlqRecordEntity d " +
           "WHERE d.status = 'REPROCESSED'")
    Double calculateAverageRetryCount();

    // ========== HEALTH CHECK QUERIES ==========

    @Query("SELECT COUNT(d) FROM DlqRecordEntity d WHERE d.status = 'PENDING' " +
           "AND d.nextRetryTime < :threshold")
    long countOverdueRetries(@Param("threshold") Instant threshold);

    @Query("SELECT COUNT(d) FROM DlqRecordEntity d WHERE d.status = 'PENDING' " +
           "AND d.createdAt < :threshold")
    long countStuckMessages(@Param("threshold") LocalDateTime threshold);

    // ========== RETRY AND STATUS QUERIES ==========

    /**
     * Find records by next retry time and status for scheduled retry processing
     */
    @Query("SELECT d FROM DlqRecordEntity d WHERE d.nextRetryTime < :retryTime AND d.status = :status " +
           "ORDER BY d.nextRetryTime ASC")
    List<DlqRecordEntity> findByNextRetryTimeBeforeAndStatus(
            @Param("retryTime") Instant retryTime,
            @Param("status") DlqStatus status
    );

    /**
     * Find records by status and created before date for cleanup operations
     */
    @Query("SELECT d FROM DlqRecordEntity d WHERE d.status = :status AND d.createdAt < :createdBefore")
    List<DlqRecordEntity> findByStatusAndCreatedAtBefore(
            @Param("status") DlqStatus status,
            @Param("createdBefore") LocalDateTime createdBefore
    );

    /**
     * Delete records by status and reprocessed before date for archival cleanup
     */
    @Modifying
    @Query("DELETE FROM DlqRecordEntity d WHERE d.status = :status AND d.reprocessedAt < :reprocessedBefore")
    int deleteByStatusAndReprocessedAtBefore(
            @Param("status") DlqStatus status,
            @Param("reprocessedBefore") LocalDateTime reprocessedBefore
    );

    // ========== PROJECTION INTERFACES ==========

    interface EventTypeCount {
        DlqEventType getEventType();
        Long getCount();
    }

    interface ServiceNameCount {
        String getServiceName();
        Long getCount();
    }
}
