package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.TransactionEvent;
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
 * Repository for Transaction Event Storage (Event Sourcing)
 *
 * Stores immutable transaction events for:
 * - Event sourcing and event replay
 * - Audit trail
 * - Transaction history reconstruction
 * - Compliance reporting
 *
 * CRITICAL: This repository was missing and causing runtime NullPointerException.
 *
 * Event Sourcing Pattern:
 * - All state changes are captured as events
 * - Events are immutable
 * - Current state can be rebuilt from events
 * - Supports temporal queries
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Repository
public interface TransactionEventRepository extends JpaRepository<TransactionEvent, UUID> {

    /**
     * Find all events for a specific transaction
     * Ordered chronologically for event replay
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.transactionId = :transactionId " +
           "ORDER BY te.sequenceNumber ASC, te.occurredAt ASC")
    List<TransactionEvent> findByTransactionIdOrderBySequence(@Param("transactionId") UUID transactionId);

    /**
     * Find events by type
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.eventType = :eventType " +
           "ORDER BY te.occurredAt DESC")
    Page<TransactionEvent> findByEventType(@Param("eventType") String eventType, Pageable pageable);

    /**
     * Find events for a transaction after a specific sequence number
     * Used for incremental event replay
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.transactionId = :transactionId " +
           "AND te.sequenceNumber > :afterSequence " +
           "ORDER BY te.sequenceNumber ASC")
    List<TransactionEvent> findByTransactionIdAfterSequence(
        @Param("transactionId") UUID transactionId,
        @Param("afterSequence") Long afterSequence
    );

    /**
     * Find events within date range
     * Used for period-based analysis
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.occurredAt BETWEEN :startDate AND :endDate " +
           "ORDER BY te.occurredAt ASC")
    List<TransactionEvent> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find events by aggregate ID (for event-sourced aggregates)
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.aggregateId = :aggregateId " +
           "ORDER BY te.sequenceNumber ASC")
    List<TransactionEvent> findByAggregateId(@Param("aggregateId") String aggregateId);

    /**
     * Get latest event for a transaction
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.transactionId = :transactionId " +
           "ORDER BY te.sequenceNumber DESC LIMIT 1")
    TransactionEvent findLatestByTransactionId(@Param("transactionId") UUID transactionId);

    /**
     * Find events by correlation ID
     * Used to track related events across services
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.correlationId = :correlationId " +
           "ORDER BY te.occurredAt ASC")
    List<TransactionEvent> findByCorrelationId(@Param("correlationId") String correlationId);

    /**
     * Find events by user ID
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.userId = :userId " +
           "ORDER BY te.occurredAt DESC")
    Page<TransactionEvent> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Count events by type for a period
     */
    @Query("SELECT te.eventType, COUNT(te) FROM TransactionEvent te " +
           "WHERE te.occurredAt BETWEEN :startDate AND :endDate " +
           "GROUP BY te.eventType")
    List<Object[]> countEventsByTypeBetweenDates(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find events published to Kafka but not processed
     * Used for dead letter queue recovery
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.published = true " +
           "AND te.processed = false " +
           "AND te.occurredAt < :cutoffDate")
    List<TransactionEvent> findUnprocessedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find events requiring snapshot
     * For performance optimization of event replay
     */
    @Query("SELECT te.transactionId FROM TransactionEvent te " +
           "GROUP BY te.transactionId " +
           "HAVING COUNT(te) > :eventThreshold " +
           "AND MAX(te.snapshotVersion) IS NULL")
    List<UUID> findTransactionsRequiringSnapshot(@Param("eventThreshold") Long eventThreshold);

    /**
     * Get next sequence number for a transaction
     */
    @Query("SELECT COALESCE(MAX(te.sequenceNumber), 0) + 1 FROM TransactionEvent te " +
           "WHERE te.transactionId = :transactionId")
    Long getNextSequenceNumber(@Param("transactionId") UUID transactionId);

    /**
     * Find events by metadata key-value
     * Supports querying by custom metadata fields
     */
    @Query("SELECT te FROM TransactionEvent te WHERE " +
           "CAST(FUNCTION('JSON_EXTRACT', te.metadata, :jsonPath) AS string) = :value")
    List<TransactionEvent> findByMetadata(
        @Param("jsonPath") String jsonPath,
        @Param("value") String value
    );

    /**
     * Find failed events requiring retry
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.published = false " +
           "AND te.retryCount < :maxRetries " +
           "AND te.occurredAt < :cutoffDate")
    List<TransactionEvent> findFailedEventsForRetry(
        @Param("maxRetries") Integer maxRetries,
        @Param("cutoffDate") LocalDateTime cutoffDate
    );

    /**
     * Check if event with idempotency key exists
     */
    @Query("SELECT CASE WHEN COUNT(te) > 0 THEN true ELSE false END " +
           "FROM TransactionEvent te WHERE te.idempotencyKey = :idempotencyKey")
    boolean existsByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * Find events for audit log export
     */
    @Query("SELECT te FROM TransactionEvent te WHERE te.occurredAt BETWEEN :startDate AND :endDate " +
           "AND te.eventType IN :eventTypes " +
           "ORDER BY te.occurredAt ASC")
    List<TransactionEvent> findForAuditExport(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("eventTypes") List<String> eventTypes
    );
}
