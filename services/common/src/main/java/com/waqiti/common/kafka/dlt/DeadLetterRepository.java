package com.waqiti.common.kafka.dlt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Dead Letter Records
 *
 * @author Waqiti Platform Engineering
 */
@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterRecord, UUID> {

    /**
     * Find DLT records by topic
     */
    Page<DeadLetterRecord> findByOriginalTopic(String topic, Pageable pageable);

    /**
     * Find DLT records by investigation status
     */
    Page<DeadLetterRecord> findByInvestigationStatus(
        DeadLetterRecord.InvestigationStatus status,
        Pageable pageable
    );

    /**
     * Find DLT records by topic and status
     */
    Page<DeadLetterRecord> findByOriginalTopicAndInvestigationStatus(
        String topic,
        DeadLetterRecord.InvestigationStatus status,
        Pageable pageable
    );

    /**
     * Find DLT record by exact topic, partition, offset
     */
    Optional<DeadLetterRecord> findByOriginalTopicAndOriginalPartitionAndOriginalOffset(
        String topic,
        Integer partition,
        Long offset
    );

    /**
     * Find DLT records that failed after a certain time
     */
    List<DeadLetterRecord> findByFailureTimestampAfter(Instant timestamp);

    /**
     * Find unreplayed DLT records
     */
    @Query("SELECT d FROM DeadLetterRecord d WHERE d.replayed = false " +
           "AND d.investigationStatus = :status ORDER BY d.failureTimestamp ASC")
    List<DeadLetterRecord> findUnreplayedByStatus(
        @Param("status") DeadLetterRecord.InvestigationStatus status
    );

    /**
     * Count DLT records by status
     */
    Long countByInvestigationStatus(DeadLetterRecord.InvestigationStatus status);

    /**
     * Count DLT records by topic
     */
    Long countByOriginalTopic(String topic);

    /**
     * Get DLT records statistics by topic
     */
    @Query("SELECT d.originalTopic as topic, COUNT(d) as count, " +
           "MIN(d.failureTimestamp) as firstFailure, " +
           "MAX(d.failureTimestamp) as lastFailure " +
           "FROM DeadLetterRecord d " +
           "GROUP BY d.originalTopic " +
           "ORDER BY count DESC")
    List<DltStatistics> getDltStatisticsByTopic();

    /**
     * Get DLT records statistics by exception
     */
    @Query("SELECT d.failureException as exception, COUNT(d) as count " +
           "FROM DeadLetterRecord d " +
           "GROUP BY d.failureException " +
           "ORDER BY count DESC")
    List<DltExceptionStatistics> getDltStatisticsByException();

    /**
     * Interface for DLT statistics projection
     */
    interface DltStatistics {
        String getTopic();
        Long getCount();
        Instant getFirstFailure();
        Instant getLastFailure();
    }

    /**
     * Interface for DLT exception statistics projection
     */
    interface DltExceptionStatistics {
        String getException();
        Long getCount();
    }
}
