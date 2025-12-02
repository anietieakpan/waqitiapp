package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Find all ledger entries for a specific transaction
     */
    List<LedgerEntry> findByTransactionIdOrderByCreatedAt(String transactionId);

    /**
     * Find all ledger entries for a specific account
     */
    Page<LedgerEntry> findByAccountIdOrderByEntryDateDesc(String accountId, Pageable pageable);

    /**
     * Find ledger entries for account within date range
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.accountId = :accountId " +
           "AND le.entryDate BETWEEN :fromDate AND :toDate " +
           "ORDER BY le.entryDate DESC")
    List<LedgerEntry> findByAccountIdAndEntryDateBetween(
            @Param("accountId") String accountId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate);

    /**
     * Calculate account balance up to a specific date
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount " +
           "WHEN le.entryType = 'DEBIT' THEN -le.amount ELSE 0 END), 0) " +
           "FROM LedgerEntry le WHERE le.accountId = :accountId " +
           "AND le.entryDate <= :asOfDate AND le.isReversal = false")
    BigDecimal calculateAccountBalanceAsOf(
            @Param("accountId") String accountId,
            @Param("asOfDate") Instant asOfDate);

    /**
     * Get the latest ledger entry for an account
     */
    Optional<LedgerEntry> findFirstByAccountIdOrderByEntryDateDesc(String accountId);

    /**
     * Find entries by reference ID
     */
    List<LedgerEntry> findByReferenceIdOrderByCreatedAt(String referenceId);

    /**
     * Check if a transaction has been fully posted (both debit and credit entries exist)
     */
    @Query("SELECT COUNT(le) FROM LedgerEntry le WHERE le.transactionId = :transactionId")
    long countByTransactionId(@Param("transactionId") String transactionId);

    /**
     * Find entries that need reconciliation
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.balanceAfter IS NULL " +
           "ORDER BY le.entryDate ASC")
    List<LedgerEntry> findEntriesNeedingReconciliation();

    /**
     * Validate double entry accounting for a transaction
     */
    @Query("SELECT SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount " +
           "WHEN le.entryType = 'DEBIT' THEN -le.amount ELSE 0 END) " +
           "FROM LedgerEntry le WHERE le.transactionId = :transactionId " +
           "AND le.isReversal = false")
    BigDecimal validateTransactionBalance(@Param("transactionId") String transactionId);

    /**
     * Find all reversals for original entries
     */
    List<LedgerEntry> findByOriginalEntryIdOrderByCreatedAt(UUID originalEntryId);

    /**
     * Get account activity summary
     */
    @Query("SELECT le.currency, COUNT(le), SUM(CASE WHEN le.entryType = 'DEBIT' THEN le.amount ELSE 0 END), " +
           "SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE 0 END) " +
           "FROM LedgerEntry le WHERE le.accountId = :accountId " +
           "AND le.entryDate BETWEEN :fromDate AND :toDate " +
           "GROUP BY le.currency")
    List<Object[]> getAccountActivitySummary(
            @Param("accountId") String accountId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate);

    /**
     * Find high-value transactions for audit
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.amount >= :threshold " +
           "AND le.entryDate BETWEEN :fromDate AND :toDate " +
           "ORDER BY le.amount DESC")
    List<LedgerEntry> findHighValueTransactions(
            @Param("threshold") BigDecimal threshold,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate);

    // Additional methods needed by DoubleEntryLedgerService

    /**
     * Find ledger entries by account ID ordered by transaction date
     */
    List<LedgerEntry> findByAccountIdOrderByTransactionDateAsc(UUID accountId);

    /**
     * Find ledger entries by account ID and date range
     */
    List<LedgerEntry> findByAccountIdAndTransactionDateBetween(UUID accountId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find ledger entries by account ID and date range with limit and offset
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.accountId = :accountId " +
           "AND le.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY le.transactionDate ASC")
    List<LedgerEntry> findByAccountIdAndDateRange(@Param("accountId") UUID accountId, 
                                                @Param("startDate") LocalDateTime startDate, 
                                                @Param("endDate") LocalDateTime endDate, 
                                                @Param("offset") int offset, 
                                                @Param("limit") int limit);

    /**
     * Count ledger entries by account ID and date range
     */
    long countByAccountIdAndTransactionDateBetween(UUID accountId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find ledger entries by account ID before specific date
     */
    List<LedgerEntry> findByAccountIdAndTransactionDateLessThanOrderByTransactionDateAsc(UUID accountId, LocalDateTime date);

    /**
     * Find ledger entries by account ID after specific date
     */
    List<LedgerEntry> findByAccountIdAndTransactionDateGreaterThanEqual(UUID accountId, LocalDateTime date);

    /**
     * Find ledger entries by account ID after specific date (descending order)
     */
    List<LedgerEntry> findByAccountIdAndTransactionDateGreaterThanEqualOrderByTransactionDateDesc(UUID accountId, LocalDateTime date);

    /**
     * Find ledger entries by journal entry ID
     */
    List<LedgerEntry> findByJournalEntryIdOrderByCreatedAtAsc(UUID journalEntryId);

    /**
     * Count ledger entries by account ID
     */
    long countByAccountId(UUID accountId);

    /**
     * Generate trial balance data
     */
    @Query("SELECT le.accountId, " +
           "SUM(CASE WHEN le.entryType = 'DEBIT' THEN le.amount ELSE 0 END) as debits, " +
           "SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE 0 END) as credits " +
           "FROM LedgerEntry le " +
           "WHERE le.status = 'POSTED' AND le.transactionDate <= :asOfDate " +
           "GROUP BY le.accountId")
    List<Object[]> getTrialBalance(@Param("asOfDate") LocalDateTime asOfDate);

    /**
     * Check if account balance exists
     */
    @Query("SELECT COUNT(le) > 0 FROM LedgerEntry le WHERE le.accountId = :accountId")
    boolean existsByAccountId(@Param("accountId") UUID accountId);

    /**
     * Find ledger entries by transaction ID (UUID version)
     */
    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
    
    /**
     * Find ledger entries by account ID up to and including specific date, ordered by transaction date
     */
    List<LedgerEntry> findByAccountIdAndTransactionDateLessThanEqualOrderByTransactionDateAsc(UUID accountId, LocalDateTime date);

    /**
     * Find ledger entries by account ID created after specific date
     */
    List<LedgerEntry> findByAccountIdAndCreatedAtAfter(UUID accountId, LocalDateTime date);
}