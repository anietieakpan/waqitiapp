package com.waqiti.user.kafka.dlq.repository;

import com.waqiti.user.kafka.dlq.DlqSeverityLevel;
import com.waqiti.user.kafka.dlq.entity.DlqEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for DLQ events
 * Provides queries for monitoring and manual intervention
 */
@Repository
public interface DlqEventRepository extends JpaRepository<DlqEvent, UUID> {

    /**
     * Find events requiring manual intervention
     */
    @Query("SELECT d FROM DlqEvent d WHERE d.requiresManualIntervention = true " +
            "AND d.processedAt IS NULL ORDER BY d.severity, d.createdAt")
    List<DlqEvent> findEventsRequiringManualIntervention();

    /**
     * Find unprocessed events by severity
     */
    @Query("SELECT d FROM DlqEvent d WHERE d.severity = :severity " +
            "AND d.processedAt IS NULL ORDER BY d.createdAt")
    List<DlqEvent> findUnprocessedBySeverity(@Param("severity") DlqSeverityLevel severity);

    /**
     * Find events by business identifier
     */
    List<DlqEvent> findByBusinessIdentifierOrderByCreatedAtDesc(String businessIdentifier);

    /**
     * Find events by event type within time range
     */
    @Query("SELECT d FROM DlqEvent d WHERE d.eventType = :eventType " +
            "AND d.createdAt BETWEEN :startTime AND :endTime ORDER BY d.createdAt DESC")
    List<DlqEvent> findByEventTypeAndTimeRange(
            @Param("eventType") String eventType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find events that need retry
     */
    @Query("SELECT d FROM DlqEvent d WHERE d.recoveryStatus = 'FAILED_RETRY' " +
            "AND d.retryAttempts < 5 AND d.processedAt IS NOT NULL " +
            "ORDER BY d.createdAt")
    List<DlqEvent> findEventsForRetry();

    /**
     * Count unprocessed critical events
     */
    @Query("SELECT COUNT(d) FROM DlqEvent d WHERE d.severity = 'CRITICAL' " +
            "AND d.processedAt IS NULL")
    long countUnprocessedCriticalEvents();

    /**
     * Count events requiring manual intervention
     */
    @Query("SELECT COUNT(d) FROM DlqEvent d WHERE d.requiresManualIntervention = true " +
            "AND d.processedAt IS NULL")
    long countEventsRequiringManualIntervention();

    /**
     * Find recent events (last 24 hours) by severity
     */
    @Query("SELECT d FROM DlqEvent d WHERE d.severity = :severity " +
            "AND d.createdAt > :since ORDER BY d.createdAt DESC")
    Page<DlqEvent> findRecentBySeverity(
            @Param("severity") DlqSeverityLevel severity,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    /**
     * Find events for batch processing
     */
    @Query("SELECT d FROM DlqEvent d WHERE d.recoveryStatus = 'QUEUED_FOR_BATCH' " +
            "AND d.processedAt IS NULL ORDER BY d.createdAt")
    List<DlqEvent> findEventsForBatchProcessing();

    /**
     * Delete processed events older than retention period
     */
    @Query("DELETE FROM DlqEvent d WHERE d.processedAt IS NOT NULL " +
            "AND d.processedAt < :retentionDate")
    void deleteProcessedEventsOlderThan(@Param("retentionDate") LocalDateTime retentionDate);
}
