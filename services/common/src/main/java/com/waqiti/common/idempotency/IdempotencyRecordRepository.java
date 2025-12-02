package com.waqiti.common.idempotency;

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
 * Repository for idempotency records with optimized queries for high-performance lookups
 *
 * Performance Characteristics:
 * - findByIdempotencyKey: <5ms (unique index)
 * - checkAndLock: <10ms (SELECT FOR UPDATE with timeout)
 * - Batch operations: optimized for cleanup jobs
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-01
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Find idempotency record by key (most common query - heavily cached)
     *
     * @param idempotencyKey Unique idempotency key
     * @return Optional idempotency record
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Check if idempotency key exists (faster than findBy for existence checks)
     *
     * @param idempotencyKey Unique idempotency key
     * @return true if exists
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Find idempotency record with pessimistic write lock for atomic check-and-create
     * Used for database-level duplicate prevention during concurrent requests
     *
     * @param idempotencyKey Unique idempotency key
     * @return Optional locked idempotency record
     */
    @Query("SELECT ir FROM IdempotencyRecord ir WHERE ir.idempotencyKey = :key " +
           "FOR UPDATE NOWAIT")
    Optional<IdempotencyRecord> findByIdempotencyKeyWithLock(@Param("key") String idempotencyKey);

    /**
     * Find all records for a specific user and operation type
     * Used for debugging and audit purposes
     *
     * @param userId User ID
     * @param operationType Operation type
     * @return List of idempotency records
     */
    List<IdempotencyRecord> findByUserIdAndOperationType(String userId, String operationType);

    /**
     * Find all records for a specific service and operation
     *
     * @param serviceName Service name
     * @param operationType Operation type
     * @return List of idempotency records
     */
    List<IdempotencyRecord> findByServiceNameAndOperationType(String serviceName, String operationType);

    /**
     * Find all in-progress operations (for timeout/cleanup)
     *
     * @param status Status filter
     * @return List of in-progress records
     */
    List<IdempotencyRecord> findByStatus(IdempotencyStatus status);

    /**
     * Find all records that have expired but not been cleaned up
     * Used by cleanup job
     *
     * @param now Current time
     * @return List of expired records
     */
    @Query("SELECT ir FROM IdempotencyRecord ir WHERE ir.expiresAt < :now " +
           "AND ir.status NOT IN ('EXPIRED', 'COMPLETED', 'FAILED')")
    List<IdempotencyRecord> findExpiredRecords(@Param("now") LocalDateTime now);

    /**
     * Find all records older than retention period for hard deletion
     *
     * @param cutoffDate Retention cutoff date
     * @return List of old records
     */
    @Query("SELECT ir FROM IdempotencyRecord ir WHERE ir.createdAt < :cutoffDate")
    List<IdempotencyRecord> findRecordsOlderThan(@Param("cutoffDate") java.time.Instant cutoffDate);

    /**
     * Find records by correlation ID (for distributed transaction tracking)
     *
     * @param correlationId Correlation/trace ID
     * @return List of related records
     */
    List<IdempotencyRecord> findByCorrelationId(String correlationId);

    /**
     * Find records for a specific operation ID
     *
     * @param operationId Operation UUID
     * @return Optional idempotency record
     */
    Optional<IdempotencyRecord> findByOperationId(UUID operationId);

    /**
     * Count records by status for monitoring
     *
     * @param status Status to count
     * @return Count of records
     */
    long countByStatus(IdempotencyStatus status);

    /**
     * Count records created in a time range (for metrics)
     *
     * @param start Start time
     * @param end End time
     * @return Count of records
     */
    @Query("SELECT COUNT(ir) FROM IdempotencyRecord ir " +
           "WHERE ir.createdAt BETWEEN :start AND :end")
    long countByCreatedAtBetween(@Param("start") java.time.Instant start,
                                  @Param("end") java.time.Instant end);

    /**
     * Find duplicate requests (same request hash, different idempotency keys)
     * Used for fraud detection
     *
     * @param requestHash Hash of request payload
     * @return List of records with matching hash
     */
    @Query("SELECT ir FROM IdempotencyRecord ir WHERE ir.requestHash = :hash " +
           "AND ir.status = 'COMPLETED' " +
           "ORDER BY ir.createdAt DESC")
    List<IdempotencyRecord> findByRequestHash(@Param("hash") String requestHash);

    /**
     * Mark expired records (batch update for cleanup job)
     *
     * @param now Current time
     * @return Number of records updated
     */
    @Modifying
    @Query("UPDATE IdempotencyRecord ir SET ir.status = 'EXPIRED' " +
           "WHERE ir.expiresAt < :now " +
           "AND ir.status NOT IN ('EXPIRED', 'COMPLETED', 'FAILED')")
    int markExpiredRecords(@Param("now") LocalDateTime now);

    /**
     * Delete old completed records (retention policy enforcement)
     *
     * @param cutoffDate Delete records older than this
     * @return Number of records deleted
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord ir " +
           "WHERE ir.createdAt < :cutoffDate " +
           "AND ir.status IN ('COMPLETED', 'FAILED', 'EXPIRED')")
    int deleteOldRecords(@Param("cutoffDate") java.time.Instant cutoffDate);

    /**
     * Find stuck operations (in-progress for too long)
     *
     * @param threshold Time threshold for considering operation stuck
     * @return List of stuck records
     */
    @Query("SELECT ir FROM IdempotencyRecord ir " +
           "WHERE ir.status = 'IN_PROGRESS' " +
           "AND ir.createdAt < :threshold")
    List<IdempotencyRecord> findStuckOperations(@Param("threshold") java.time.Instant threshold);

    /**
     * Find operations with high retry counts (for alerting)
     *
     * @param minRetries Minimum retry count
     * @return List of frequently retried operations
     */
    @Query("SELECT ir FROM IdempotencyRecord ir " +
           "WHERE ir.retryCount >= :minRetries " +
           "ORDER BY ir.retryCount DESC")
    List<IdempotencyRecord> findHighRetryOperations(@Param("minRetries") int minRetries);

    /**
     * Get statistics for monitoring dashboard
     *
     * @return Map of status to count
     */
    @Query("SELECT ir.status, COUNT(ir) FROM IdempotencyRecord ir " +
           "GROUP BY ir.status")
    List<Object[]> getStatusStatistics();

    /**
     * Get statistics by service
     *
     * @return List of [serviceName, operationType, count]
     */
    @Query("SELECT ir.serviceName, ir.operationType, COUNT(ir) " +
           "FROM IdempotencyRecord ir " +
           "GROUP BY ir.serviceName, ir.operationType " +
           "ORDER BY COUNT(ir) DESC")
    List<Object[]> getServiceStatistics();

    /**
     * Find records for specific user within time range (audit query)
     *
     * @param userId User ID
     * @param start Start time
     * @param end End time
     * @return List of records
     */
    @Query("SELECT ir FROM IdempotencyRecord ir " +
           "WHERE ir.userId = :userId " +
           "AND ir.createdAt BETWEEN :start AND :end " +
           "ORDER BY ir.createdAt DESC")
    List<IdempotencyRecord> findByUserIdAndTimeRange(
        @Param("userId") String userId,
        @Param("start") java.time.Instant start,
        @Param("end") java.time.Instant end
    );

    /**
     * Find records for a specific IP address (security audit)
     *
     * @param ipAddress Client IP address
     * @return List of records
     */
    List<IdempotencyRecord> findByClientIpAddress(String ipAddress);

    /**
     * Find records for a specific device fingerprint (fraud detection)
     *
     * @param deviceFingerprint Device fingerprint
     * @return List of records
     */
    List<IdempotencyRecord> findByDeviceFingerprint(String deviceFingerprint);

    /**
     * Count operations by user in time window (rate limiting support)
     *
     * @param userId User ID
     * @param operationType Operation type
     * @param since Time window start
     * @return Count of operations
     */
    @Query("SELECT COUNT(ir) FROM IdempotencyRecord ir " +
           "WHERE ir.userId = :userId " +
           "AND ir.operationType = :operationType " +
           "AND ir.createdAt >= :since")
    long countByUserIdAndOperationTypeAndCreatedAtAfter(
        @Param("userId") String userId,
        @Param("operationType") String operationType,
        @Param("since") java.time.Instant since
    );

    /**
     * Find failed operations that can be retried
     *
     * @param maxRetries Maximum retry threshold
     * @return List of retryable failed operations
     */
    @Query("SELECT ir FROM IdempotencyRecord ir " +
           "WHERE ir.status = 'RETRYABLE_FAILED' " +
           "AND (ir.retryCount IS NULL OR ir.retryCount < :maxRetries) " +
           "ORDER BY ir.createdAt ASC")
    List<IdempotencyRecord> findRetryableFailedOperations(@Param("maxRetries") int maxRetries);
}
