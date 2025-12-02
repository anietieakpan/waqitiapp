package com.waqiti.common.compensation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for persisting compensation transactions.
 * Provides durability and audit trail for all compensation operations.
 */
@Repository
public interface CompensationRepository extends JpaRepository<CompensationTransactionEntity, String> {

    /**
     * Find compensations by status
     */
    List<CompensationTransactionEntity> findByStatus(CompensationTransaction.CompensationStatus status);

    /**
     * Find compensations by original transaction ID
     */
    List<CompensationTransactionEntity> findByOriginalTransactionId(String originalTransactionId);

    /**
     * Find compensations by priority
     */
    List<CompensationTransactionEntity> findByPriority(
        CompensationTransaction.CompensationPriority priority);

    /**
     * Find stuck compensations (pending for too long)
     */
    @Query("SELECT c FROM CompensationTransactionEntity c WHERE " +
           "c.status = 'PENDING' AND c.createdAt < :threshold")
    List<CompensationTransactionEntity> findStuckCompensations(
        @Param("threshold") LocalDateTime threshold);

    /**
     * Find compensations requiring retry
     */
    @Query("SELECT c FROM CompensationTransactionEntity c WHERE " +
           "c.status = 'RETRYING' AND c.lastAttemptAt < :retryThreshold")
    List<CompensationTransactionEntity> findCompensationsForRetry(
        @Param("retryThreshold") LocalDateTime retryThreshold);

    /**
     * Count compensations by status
     */
    long countByStatus(CompensationTransaction.CompensationStatus status);

    /**
     * Find recent compensations for monitoring
     */
    @Query("SELECT c FROM CompensationTransactionEntity c WHERE " +
           "c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<CompensationTransactionEntity> findRecentCompensations(
        @Param("since") LocalDateTime since);
}
