package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.model.Discrepancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Discrepancy entities
 * Handles discrepancies found during reconciliation processes
 */
@Repository
public interface DiscrepancyRepository extends JpaRepository<Discrepancy, String> {

    /**
     * Find discrepancies by type
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.discrepancyType = :type ORDER BY d.createdAt DESC")
    List<Discrepancy> findByDiscrepancyType(@Param("type") Discrepancy.DiscrepancyType type);

    /**
     * Find discrepancies by status
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.status = :status ORDER BY d.createdAt DESC")
    List<Discrepancy> findByStatus(@Param("status") Discrepancy.DiscrepancyStatus status);

    /**
     * Find discrepancies by transaction ID
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.transactionId = :transactionId")
    List<Discrepancy> findByTransactionId(@Param("transactionId") String transactionId);

    /**
     * Find discrepancies by provider transaction ID
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.providerTransactionId = :providerTransactionId")
    List<Discrepancy> findByProviderTransactionId(@Param("providerTransactionId") String providerTransactionId);

    /**
     * Find pending discrepancies
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.status = 'PENDING_REVIEW' ORDER BY d.amountDifference DESC")
    List<Discrepancy> findPendingReview();

    /**
     * Find discrepancies by date range
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.createdAt BETWEEN :startDate AND :endDate ORDER BY d.createdAt DESC")
    List<Discrepancy> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find high-value discrepancies
     */
    @Query("SELECT d FROM Discrepancy d WHERE ABS(d.amountDifference) > :threshold ORDER BY ABS(d.amountDifference) DESC")
    List<Discrepancy> findHighValueDiscrepancies(@Param("threshold") BigDecimal threshold);

    /**
     * Find discrepancies by confidence range (for partial matches)
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.confidence BETWEEN :minConfidence AND :maxConfidence " +
           "AND d.discrepancyType = 'PARTIAL_MATCH' ORDER BY d.confidence ASC")
    List<Discrepancy> findByConfidenceRange(
            @Param("minConfidence") Double minConfidence,
            @Param("maxConfidence") Double maxConfidence
    );

    /**
     * Find orphaned transaction discrepancies
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.discrepancyType = 'ORPHANED_TRANSACTION' ORDER BY d.createdAt DESC")
    List<Discrepancy> findOrphanedTransactions();

    /**
     * Find orphaned provider transaction discrepancies
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.discrepancyType = 'ORPHANED_PROVIDER_TRANSACTION' ORDER BY d.createdAt DESC")
    List<Discrepancy> findOrphanedProviderTransactions();

    /**
     * Find data mismatch discrepancies
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.discrepancyType = 'DATA_MISMATCH' ORDER BY d.createdAt DESC")
    List<Discrepancy> findDataMismatches();

    /**
     * Find balance mismatch discrepancies
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.discrepancyType = 'BALANCE_MISMATCH' ORDER BY ABS(d.amountDifference) DESC")
    List<Discrepancy> findBalanceMismatches();

    /**
     * Count discrepancies by type
     */
    @Query("SELECT COUNT(d) FROM Discrepancy d WHERE d.discrepancyType = :type")
    long countByType(@Param("type") Discrepancy.DiscrepancyType type);

    /**
     * Count discrepancies by status
     */
    @Query("SELECT COUNT(d) FROM Discrepancy d WHERE d.status = :status")
    long countByStatus(@Param("status") Discrepancy.DiscrepancyStatus status);

    /**
     * Find unresolved discrepancies older than threshold
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.status IN ('PENDING_REVIEW', 'UNDER_INVESTIGATION') " +
           "AND d.createdAt < :threshold ORDER BY d.createdAt ASC")
    List<Discrepancy> findOldUnresolvedDiscrepancies(@Param("threshold") LocalDateTime threshold);

    /**
     * Find discrepancies requiring escalation
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.status = 'PENDING_REVIEW' " +
           "AND (ABS(d.amountDifference) > :amountThreshold OR d.createdAt < :timeThreshold)")
    List<Discrepancy> findDiscrepanciesRequiringEscalation(
            @Param("amountThreshold") BigDecimal amountThreshold,
            @Param("timeThreshold") LocalDateTime timeThreshold
    );

    /**
     * Find discrepancies with pagination
     */
    @Query("SELECT d FROM Discrepancy d ORDER BY d.createdAt DESC")
    Page<Discrepancy> findAllWithPaging(Pageable pageable);

    /**
     * Find resolved discrepancies by date range
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.status = 'RESOLVED' " +
           "AND d.resolvedAt BETWEEN :startDate AND :endDate ORDER BY d.resolvedAt DESC")
    List<Discrepancy> findResolvedByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find discrepancies by resolver
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.resolvedBy = :resolverUserId ORDER BY d.resolvedAt DESC")
    List<Discrepancy> findByResolver(@Param("resolverUserId") String resolverUserId);

    /**
     * Calculate total discrepancy amount by status
     */
    @Query("SELECT SUM(ABS(d.amountDifference)) FROM Discrepancy d WHERE d.status = :status")
    BigDecimal getTotalDiscrepancyAmountByStatus(@Param("status") Discrepancy.DiscrepancyStatus status);

    /**
     * Find discrepancies by assigned user
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.assignedTo = :userId ORDER BY d.createdAt DESC")
    List<Discrepancy> findByAssignedUser(@Param("userId") String userId);

    /**
     * Find recent discrepancies by type
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.discrepancyType = :type " +
           "AND d.createdAt > :since ORDER BY d.createdAt DESC")
    List<Discrepancy> findRecentByType(
            @Param("type") Discrepancy.DiscrepancyType type,
            @Param("since") LocalDateTime since
    );

    /**
     * Find discrepancies by priority
     */
    @Query("SELECT d FROM Discrepancy d WHERE d.priority = :priority ORDER BY d.createdAt DESC")
    List<Discrepancy> findByPriority(@Param("priority") String priority);

    /**
     * Update discrepancy status
     */
    @Query("UPDATE Discrepancy d SET d.status = :status, d.resolvedAt = :resolvedAt, " +
           "d.resolvedBy = :resolvedBy, d.resolutionNotes = :notes WHERE d.id = :discrepancyId")
    void updateStatus(
            @Param("discrepancyId") String discrepancyId,
            @Param("status") Discrepancy.DiscrepancyStatus status,
            @Param("resolvedAt") LocalDateTime resolvedAt,
            @Param("resolvedBy") String resolvedBy,
            @Param("notes") String notes
    );

    /**
     * Assign discrepancy to user
     */
    @Query("UPDATE Discrepancy d SET d.assignedTo = :userId, d.assignedAt = :assignedAt WHERE d.id = :discrepancyId")
    void assignToUser(
            @Param("discrepancyId") String discrepancyId,
            @Param("userId") String userId,
            @Param("assignedAt") LocalDateTime assignedAt
    );

    /**
     * Get discrepancy statistics by date range
     */
    @Query("SELECT " +
           "COUNT(d) as totalDiscrepancies, " +
           "SUM(CASE WHEN d.status = 'RESOLVED' THEN 1 ELSE 0 END) as resolvedCount, " +
           "SUM(CASE WHEN d.status = 'PENDING_REVIEW' THEN 1 ELSE 0 END) as pendingCount, " +
           "SUM(ABS(d.amountDifference)) as totalAmount " +
           "FROM Discrepancy d WHERE d.createdAt BETWEEN :startDate AND :endDate")
    DiscrepancyStatisticsProjection getStatisticsByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Projection for discrepancy statistics
     */
    interface DiscrepancyStatisticsProjection {
        Long getTotalDiscrepancies();
        Long getResolvedCount();
        Long getPendingCount();
        BigDecimal getTotalAmount();
    }
}