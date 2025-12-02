package com.waqiti.payment.repository;

import com.waqiti.payment.entity.IdempotencyRecord;
import com.waqiti.payment.entity.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for idempotency records
 *
 * PERFORMANCE OPTIMIZATIONS:
 * - Unique index on idempotency_key for fast duplicate detection
 * - Index on expires_at for efficient cleanup queries
 * - Index on status for status-based queries
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Find idempotency record by unique key
     *
     * PERFORMANCE: Uses unique index on idempotency_key
     *
     * @param idempotencyKey Unique request identifier
     * @return Optional containing record if found
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Count records by status
     *
     * @param status Status to count
     * @return Number of records with given status
     */
    long countByStatus(IdempotencyStatus status);

    /**
     * Delete expired idempotency records
     *
     * CRITICAL: Prevents unbounded table growth
     * Should be called periodically (daily)
     *
     * @param cutoffTime Delete records expiring before this time
     * @return Number of records deleted
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord ir WHERE ir.expiresAt < :cutoffTime")
    int deleteByExpiresAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find processing records older than threshold (for stuck request detection)
     *
     * @param threshold Timestamp threshold
     * @return List of stuck processing records
     */
    @Query("SELECT ir FROM IdempotencyRecord ir WHERE ir.status = 'PROCESSING' AND ir.createdAt < :threshold")
    java.util.List<IdempotencyRecord> findStuckProcessingRecords(@Param("threshold") LocalDateTime threshold);
}
