package com.waqiti.payment.repository;

import com.waqiti.payment.entity.LedgerTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Ledger Transaction operations
 */
@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    /**
     * Find transaction by transaction number
     */
    Optional<LedgerTransaction> findByTransactionNumber(String transactionNumber);

    /**
     * Find transaction by saga ID
     */
    Optional<LedgerTransaction> findBySagaId(String sagaId);

    /**
     * Find transactions by date range
     */
    Page<LedgerTransaction> findByTransactionDateBetween(
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable);

    /**
     * Find transactions by status
     */
    List<LedgerTransaction> findByStatus(LedgerTransaction.TransactionStatus status);

    /**
     * Find transactions by type and status
     */
    List<LedgerTransaction> findByTransactionTypeAndStatus(
        LedgerTransaction.TransactionType type,
        LedgerTransaction.TransactionStatus status);

    /**
     * Find unbalanced transactions
     */
    @Query("SELECT lt FROM LedgerTransaction lt WHERE " +
           "lt.totalDebits != lt.totalCredits " +
           "AND lt.status NOT IN ('FAILED', 'CANCELLED')")
    List<LedgerTransaction> findUnbalancedTransactions();

    /**
     * Find transactions needing approval
     */
    @Query("SELECT lt FROM LedgerTransaction lt WHERE " +
           "lt.status = 'PENDING' " +
           "AND lt.approvedBy IS NULL " +
           "AND lt.totalDebits > :thresholdAmount")
    List<LedgerTransaction> findTransactionsNeedingApproval(@Param("thresholdAmount") BigDecimal thresholdAmount);

    /**
     * Update transaction status
     */
    @Modifying
    @Query("UPDATE LedgerTransaction lt SET " +
           "lt.status = :newStatus, " +
           "lt.updatedAt = :updatedAt " +
           "WHERE lt.id = :transactionId")
    void updateTransactionStatus(
        @Param("transactionId") UUID transactionId,
        @Param("newStatus") LedgerTransaction.TransactionStatus newStatus,
        @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Approve transaction
     */
    @Modifying
    @Query("UPDATE LedgerTransaction lt SET " +
           "lt.status = 'COMPLETED', " +
           "lt.approvedBy = :approvedBy, " +
           "lt.approvedAt = :approvedAt, " +
           "lt.updatedAt = :approvedAt " +
           "WHERE lt.id = :transactionId AND lt.status = 'PENDING'")
    int approveTransaction(
        @Param("transactionId") UUID transactionId,
        @Param("approvedBy") UUID approvedBy,
        @Param("approvedAt") LocalDateTime approvedAt);

    /**
     * Mark transaction as reversed
     */
    @Modifying
    @Query("UPDATE LedgerTransaction lt SET " +
           "lt.status = 'REVERSED', " +
           "lt.reversedBy = :reversedBy, " +
           "lt.reversedAt = :reversedAt, " +
           "lt.reversalTransactionId = :reversalTransactionId, " +
           "lt.updatedAt = :reversedAt " +
           "WHERE lt.id = :transactionId")
    void markAsReversed(
        @Param("transactionId") UUID transactionId,
        @Param("reversedBy") UUID reversedBy,
        @Param("reversedAt") LocalDateTime reversedAt,
        @Param("reversalTransactionId") UUID reversalTransactionId);

    /**
     * Find transactions by external reference
     */
    Optional<LedgerTransaction> findByExternalReference(String externalReference);

    /**
     * Get transaction summary by type
     */
    @Query("SELECT " +
           "lt.transactionType, " +
           "COUNT(lt), " +
           "SUM(lt.totalDebits) " +
           "FROM LedgerTransaction lt " +
           "WHERE lt.status = 'COMPLETED' " +
           "AND lt.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY lt.transactionType")
    List<Object[]> getTransactionSummaryByType(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Check for duplicate transaction
     */
    @Query("SELECT COUNT(lt) > 0 FROM LedgerTransaction lt WHERE " +
           "lt.checksum = :checksum " +
           "AND lt.status NOT IN ('FAILED', 'CANCELLED') " +
           "AND lt.transactionDate > :recentDate")
    boolean existsByChecksumRecent(
        @Param("checksum") String checksum,
        @Param("recentDate") LocalDateTime recentDate);

    /**
     * Find stuck transactions
     */
    @Query("SELECT lt FROM LedgerTransaction lt WHERE " +
           "lt.status = 'PROCESSING' " +
           "AND lt.updatedAt < :stuckThreshold")
    List<LedgerTransaction> findStuckTransactions(@Param("stuckThreshold") LocalDateTime stuckThreshold);

    /**
     * Get daily transaction totals
     */
    @Query("SELECT " +
           "DATE(lt.transactionDate), " +
           "COUNT(lt), " +
           "SUM(lt.totalDebits) " +
           "FROM LedgerTransaction lt " +
           "WHERE lt.status = 'COMPLETED' " +
           "AND lt.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(lt.transactionDate)")
    List<Object[]> getDailyTransactionTotals(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}