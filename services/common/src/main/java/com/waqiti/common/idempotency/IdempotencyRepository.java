package com.waqiti.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production-grade repository for persistent idempotency records
 * Supports both database persistence and Redis caching for performance
 */
@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Find idempotency record by key with pessimistic write lock
     * Critical for preventing race conditions in concurrent operations
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.idempotencyKey = :key")
    Optional<IdempotencyRecord> findByIdempotencyKeyWithLock(@Param("key") String idempotencyKey);

    /**
     * Find idempotency record by key (read-only)
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find active records for a specific user and operation type
     */
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.userId = :userId " +
           "AND i.operationType = :operationType AND i.status IN ('IN_PROGRESS', 'PENDING_APPROVAL')")
    List<IdempotencyRecord> findActiveRecordsByUserAndOperation(
            @Param("userId") String userId,
            @Param("operationType") String operationType);

    /**
     * Find records that have expired and need cleanup
     */
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.expiresAt < :now AND i.status != 'EXPIRED'")
    List<IdempotencyRecord> findExpiredRecords(@Param("now") LocalDateTime now);

    /**
     * Find records by correlation ID for audit purposes
     */
    List<IdempotencyRecord> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);

    /**
     * Find failed records that can be retried
     */
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.status = 'RETRYABLE_FAILED' " +
           "AND i.retryCount < :maxRetries AND i.lastRetryAt < :retryAfter")
    List<IdempotencyRecord> findRetryableRecords(
            @Param("maxRetries") int maxRetries,
            @Param("retryAfter") LocalDateTime retryAfter);

    /**
     * Count active operations for a user (for rate limiting)
     */
    @Query("SELECT COUNT(i) FROM IdempotencyRecord i WHERE i.userId = :userId " +
           "AND i.status IN ('IN_PROGRESS', 'PENDING_APPROVAL') " +
           "AND i.createdAt > :since")
    long countActiveOperationsForUser(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since);

    /**
     * Find records by service and operation for monitoring
     */
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.serviceName = :serviceName " +
           "AND i.operationType = :operationType AND i.createdAt > :since " +
           "ORDER BY i.createdAt DESC")
    List<IdempotencyRecord> findByServiceAndOperationSince(
            @Param("serviceName") String serviceName,
            @Param("operationType") String operationType,
            @Param("since") LocalDateTime since);

    /**
     * Bulk update expired records
     */
    @Modifying
    @Transactional
    @Query("UPDATE IdempotencyRecord i SET i.status = 'EXPIRED' " +
           "WHERE i.expiresAt < :now AND i.status != 'EXPIRED'")
    int markExpiredRecords(@Param("now") LocalDateTime now);

    /**
     * Delete old completed records for cleanup (after audit retention period)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM IdempotencyRecord i WHERE i.status IN ('COMPLETED', 'EXPIRED', 'FAILED') " +
           "AND i.completedAt < :cutoffDate")
    int deleteOldCompletedRecords(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find records with high retry count for investigation
     */
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.retryCount >= :threshold " +
           "AND i.createdAt > :since ORDER BY i.retryCount DESC")
    List<IdempotencyRecord> findHighRetryRecords(
            @Param("threshold") int threshold,
            @Param("since") LocalDateTime since);

    /**
     * Get operation statistics for monitoring
     */
    @Query("SELECT i.serviceName, i.operationType, i.status, COUNT(i) " +
           "FROM IdempotencyRecord i WHERE i.createdAt > :since " +
           "GROUP BY i.serviceName, i.operationType, i.status")
    List<Object[]> getOperationStatistics(@Param("since") LocalDateTime since);

    /**
     * Find potentially duplicate operations (same user, amount, currency within time window)
     */
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.userId = :userId " +
           "AND i.amount = :amount AND i.currency = :currency " +
           "AND i.operationType = :operationType " +
           "AND i.createdAt > :timeWindow AND i.idempotencyKey != :currentKey " +
           "ORDER BY i.createdAt DESC")
    List<IdempotencyRecord> findPotentialDuplicates(
            @Param("userId") String userId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("operationType") String operationType,
            @Param("timeWindow") LocalDateTime timeWindow,
            @Param("currentKey") String currentKey);

    /**
     * Custom method to check if operation is safe to proceed (no conflicting operations)
     */
    @Query("SELECT COUNT(i) FROM IdempotencyRecord i WHERE i.userId = :userId " +
           "AND i.operationType = :operationType AND i.status = 'IN_PROGRESS' " +
           "AND i.idempotencyKey != :currentKey")
    long countConflictingOperations(
            @Param("userId") String userId,
            @Param("operationType") String operationType,
            @Param("currentKey") String currentKey);
}