package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.domain.Settlement;
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
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    /**
     * Find settlement by external reference
     */
    Optional<Settlement> findByExternalReference(String externalReference);

    /**
     * Find settlements by account ID
     */
    List<Settlement> findByAccountIdOrderBySettlementDateDesc(UUID accountId);

    /**
     * Find settlements by account ID with pagination
     */
    Page<Settlement> findByAccountId(UUID accountId, Pageable pageable);

    /**
     * Find settlements by status
     */
    List<Settlement> findByStatusOrderBySettlementDateDesc(Settlement.SettlementStatus status);

    /**
     * Find settlements by settlement date and status (for daily batch processing)
     * Migrated from batch-service consolidation
     */
    List<Settlement> findBySettlementDateAndStatus(java.time.LocalDate settlementDate, Settlement.SettlementStatus status);

    /**
     * Find settlements by status with pagination
     */
    Page<Settlement> findByStatus(Settlement.SettlementStatus status, Pageable pageable);

    /**
     * Find settlements by settlement type
     */
    List<Settlement> findBySettlementTypeOrderBySettlementDateDesc(Settlement.SettlementType settlementType);

    /**
     * Find settlements within date range
     */
    List<Settlement> findBySettlementDateBetweenOrderBySettlementDateDesc(
        LocalDateTime startDate, 
        LocalDateTime endDate
    );

    /**
     * Find settlements within date range with pagination
     */
    Page<Settlement> findBySettlementDateBetween(
        LocalDateTime startDate, 
        LocalDateTime endDate, 
        Pageable pageable
    );

    /**
     * Find settlements by currency
     */
    List<Settlement> findByCurrencyOrderBySettlementDateDesc(String currency);

    /**
     * Find settlements requiring reconciliation
     */
    @Query("SELECT s FROM Settlement s WHERE s.status IN ('RECONCILIATION_PENDING', 'BREAK_DETECTED') ORDER BY s.settlementDate ASC")
    List<Settlement> findSettlementsRequiringReconciliation();

    /**
     * Find settlements requiring reconciliation with pagination
     */
    @Query("SELECT s FROM Settlement s WHERE s.status IN ('RECONCILIATION_PENDING', 'BREAK_DETECTED') ORDER BY s.settlementDate ASC")
    Page<Settlement> findSettlementsRequiringReconciliation(Pageable pageable);

    /**
     * Find unreconciled settlements older than specified time
     */
    @Query("SELECT s FROM Settlement s WHERE s.status NOT IN ('RECONCILED', 'CANCELLED') AND s.settlementDate < :cutoffTime ORDER BY s.settlementDate ASC")
    List<Settlement> findUnreconciledSettlementsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find settlements by counterparty
     */
    List<Settlement> findByCounterpartyIdOrderBySettlementDateDesc(UUID counterpartyId);

    /**
     * Find settlements by amount range
     */
    @Query("SELECT s FROM Settlement s WHERE s.amount BETWEEN :minAmount AND :maxAmount ORDER BY s.settlementDate DESC")
    List<Settlement> findByAmountRange(
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount
    );

    /**
     * Find settlements with external confirmation reference
     */
    List<Settlement> findByExternalConfirmationReferenceIsNotNullOrderBySettlementDateDesc();

    /**
     * Find settlements without external confirmation
     */
    List<Settlement> findByExternalConfirmationReferenceIsNullAndStatusOrderBySettlementDateDesc(Settlement.SettlementStatus status);

    /**
     * Get settlement statistics by status
     */
    @Query("SELECT s.status, COUNT(s), SUM(s.amount) FROM Settlement s WHERE s.settlementDate BETWEEN :startDate AND :endDate GROUP BY s.status")
    List<Object[]> getSettlementStatisticsByStatus(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get settlement statistics by type
     */
    @Query("SELECT s.settlementType, COUNT(s), SUM(s.amount) FROM Settlement s WHERE s.settlementDate BETWEEN :startDate AND :endDate GROUP BY s.settlementType")
    List<Object[]> getSettlementStatisticsByType(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get settlement statistics by currency
     */
    @Query("SELECT s.currency, COUNT(s), SUM(s.amount) FROM Settlement s WHERE s.settlementDate BETWEEN :startDate AND :endDate GROUP BY s.currency")
    List<Object[]> getSettlementStatisticsByCurrency(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find settlements reconciled within date range
     */
    @Query("SELECT s FROM Settlement s WHERE s.status = 'RECONCILED' AND s.reconciledAt BETWEEN :startDate AND :endDate ORDER BY s.reconciledAt DESC")
    List<Settlement> findReconciledSettlementsInRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count settlements by account and status
     */
    @Query("SELECT COUNT(s) FROM Settlement s WHERE s.accountId = :accountId AND s.status = :status")
    Long countByAccountIdAndStatus(
        @Param("accountId") UUID accountId,
        @Param("status") Settlement.SettlementStatus status
    );

    /**
     * Find duplicate settlements (same external reference)
     */
    @Query("SELECT s FROM Settlement s WHERE s.externalReference IN (SELECT s2.externalReference FROM Settlement s2 GROUP BY s2.externalReference HAVING COUNT(s2) > 1) ORDER BY s.externalReference, s.settlementDate")
    List<Settlement> findDuplicateSettlements();

    /**
     * Find settlements with breaks
     */
    List<Settlement> findByStatusOrderBySettlementDateDesc(Settlement.SettlementStatus status);

    /**
     * Calculate total settlement amount by currency and date range
     */
    @Query("SELECT s.currency, SUM(s.amount) FROM Settlement s WHERE s.status = 'RECONCILED' AND s.settlementDate BETWEEN :startDate AND :endDate GROUP BY s.currency")
    List<Object[]> getTotalSettlementAmountByCurrency(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find settlements pending for more than specified hours
     */
    @Query("SELECT s FROM Settlement s WHERE s.status IN ('PENDING', 'PROCESSING') AND s.createdAt < :cutoffTime ORDER BY s.createdAt ASC")
    List<Settlement> findLongPendingSettlements(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find failed settlements within date range
     */
    @Query("SELECT s FROM Settlement s WHERE s.status = 'FAILED' AND s.settlementDate BETWEEN :startDate AND :endDate ORDER BY s.settlementDate DESC")
    List<Settlement> findFailedSettlementsInRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calculate average settlement amount by type
     */
    @Query("SELECT s.settlementType, AVG(s.amount) FROM Settlement s WHERE s.settlementDate BETWEEN :startDate AND :endDate GROUP BY s.settlementType")
    List<Object[]> getAverageSettlementAmountByType(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find settlements that need escalation
     */
    @Query("SELECT s FROM Settlement s WHERE s.status IN ('RECONCILIATION_PENDING', 'BREAK_DETECTED') AND s.settlementDate < :escalationTime ORDER BY s.settlementDate ASC")
    List<Settlement> findSettlementsForEscalation(@Param("escalationTime") LocalDateTime escalationTime);

    /**
     * Check if settlement exists with external reference
     */
    boolean existsByExternalReference(String externalReference);

    /**
     * Delete old completed settlements (for cleanup)
     */
    @Query("DELETE FROM Settlement s WHERE s.status IN ('RECONCILED', 'CANCELLED') AND s.updatedAt < :cutoffDate")
    int deleteOldCompletedSettlements(@Param("cutoffDate") LocalDateTime cutoffDate);
}