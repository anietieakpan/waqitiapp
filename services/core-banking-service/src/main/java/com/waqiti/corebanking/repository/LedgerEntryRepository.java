package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ledger Entry Repository
 * 
 * Repository interface for LedgerEntry entity operations
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Find entries by transaction ID with transaction details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "LedgerEntry.withTransactionAndAccount", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT le FROM LedgerEntry le " +
           "WHERE le.transactionId = :transactionId ORDER BY le.entryNumber")
    List<LedgerEntry> findByTransactionIdOrderByEntryNumber(@Param("transactionId") UUID transactionId);

    /**
     * Find entries by account ID with account and transaction details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "LedgerEntry.withTransactionAndAccount", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT le FROM LedgerEntry le " +
           "WHERE le.accountId = :accountId ORDER BY le.entryDate DESC")
    Page<LedgerEntry> findByAccountIdOrderByEntryDateDesc(@Param("accountId") UUID accountId, Pageable pageable);

    /**
     * Find entries by account ID and date range with account and transaction details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "LedgerEntry.withTransactionAndAccount", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT le FROM LedgerEntry le " +
           "WHERE le.accountId = :accountId AND le.entryDate BETWEEN :startDate AND :endDate ORDER BY le.entryDate DESC")
    List<LedgerEntry> findByAccountIdAndDateRange(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find entries by status
     */
    List<LedgerEntry> findByStatus(LedgerEntry.EntryStatus status);

    /**
     * Find entries by entry type
     */
    List<LedgerEntry> findByEntryType(LedgerEntry.EntryType entryType);

    /**
     * Find entries by reference
     */
    List<LedgerEntry> findByReference(String reference);

    /**
     * Find entries by external reference
     */
    List<LedgerEntry> findByExternalReference(String externalReference);

    /**
     * Calculate account balance at specific date
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE -le.amount END), 0) " +
           "FROM LedgerEntry le WHERE le.accountId = :accountId AND le.entryDate <= :asOfDate AND le.status = 'POSTED'")
    BigDecimal calculateAccountBalanceAsOf(@Param("accountId") UUID accountId, @Param("asOfDate") LocalDateTime asOfDate);

    /**
     * Get latest entry for account
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.accountId = :accountId ORDER BY le.entryDate DESC, le.entryNumber DESC")
    List<LedgerEntry> findLatestEntryForAccount(@Param("accountId") UUID accountId, Pageable pageable);

    /**
     * Find unreconciled entries
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.reconciliationId IS NULL AND le.status = 'POSTED'")
    List<LedgerEntry> findUnreconciledEntries();

    /**
     * Find entries pending posting
     */
    List<LedgerEntry> findByStatusAndEntryDateBefore(LedgerEntry.EntryStatus status, LocalDateTime cutoffDate);

    /**
     * Get account transaction history with related entities
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "LedgerEntry.withTransactionAndAccount", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT le FROM LedgerEntry le " +
           "WHERE le.accountId = :accountId AND le.status = 'POSTED' ORDER BY le.entryDate DESC")
    Page<LedgerEntry> findAccountTransactionHistory(@Param("accountId") UUID accountId, Pageable pageable);

    /**
     * Find entries by amount range
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.amount BETWEEN :minAmount AND :maxAmount")
    List<LedgerEntry> findByAmountRange(@Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount);

    /**
     * Get daily transaction summary
     */
    @Query("SELECT le.entryType, COUNT(le), SUM(le.amount) FROM LedgerEntry le " +
           "WHERE le.entryDate BETWEEN :startDate AND :endDate AND le.status = 'POSTED' " +
           "GROUP BY le.entryType")
    List<Object[]> getDailyTransactionSummary(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find reversed entries
     */
    List<LedgerEntry> findByReversalEntryIdIsNotNull();

    /**
     * Find original entries for reversals
     */
    List<LedgerEntry> findByOriginalEntryId(UUID originalEntryId);

    /**
     * Validate transaction balance
     */
    @Query("SELECT SUM(CASE WHEN le.entryType = 'DEBIT' THEN le.amount ELSE -le.amount END) " +
           "FROM LedgerEntry le WHERE le.transactionId = :transactionId")
    BigDecimal validateTransactionBalance(@Param("transactionId") UUID transactionId);

    /**
     * Find entries by currency
     */
    List<LedgerEntry> findByCurrency(String currency);

    /**
     * Get account balance movements
     */
    @Query("SELECT le.entryDate, le.runningBalance FROM LedgerEntry le " +
           "WHERE le.accountId = :accountId AND le.status = 'POSTED' " +
           "ORDER BY le.entryDate ASC")
    List<Object[]> getAccountBalanceMovements(@Param("accountId") UUID accountId);

    /**
     * Find high-value entries
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.amount > :threshold ORDER BY le.amount DESC")
    List<LedgerEntry> findHighValueEntries(@Param("threshold") BigDecimal threshold);

    /**
     * Count entries by account and date range
     */
    @Query("SELECT COUNT(le) FROM LedgerEntry le WHERE le.accountId = :accountId " +
           "AND le.entryDate BETWEEN :startDate AND :endDate AND le.status = 'POSTED'")
    long countEntriesInDateRange(
        @Param("accountId") UUID accountId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
}