package com.waqiti.payment.repository;

import com.waqiti.payment.entity.JournalEntry;
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
import java.util.UUID;

/**
 * Repository for Journal Entry operations
 */
@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    /**
     * Find entries by transaction ID
     */
    List<JournalEntry> findByTransactionIdOrderBySequenceNumber(UUID transactionId);

    /**
     * Find entries by account
     */
    Page<JournalEntry> findByAccountIdOrderByEntryDateDesc(UUID accountId, Pageable pageable);

    /**
     * Find entries by account and date range
     */
    @Query("SELECT je FROM JournalEntry je WHERE " +
           "je.account.id = :accountId " +
           "AND je.entryDate BETWEEN :startDate AND :endDate " +
           "ORDER BY je.entryDate DESC")
    List<JournalEntry> findByAccountAndDateRange(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Calculate account balance up to a specific date
     */
    @Query("SELECT " +
           "COALESCE(SUM(CASE WHEN je.entryType = 'DEBIT' THEN je.amount ELSE 0 END), 0) - " +
           "COALESCE(SUM(CASE WHEN je.entryType = 'CREDIT' THEN je.amount ELSE 0 END), 0) " +
           "FROM JournalEntry je " +
           "WHERE je.account.id = :accountId " +
           "AND je.status = 'POSTED' " +
           "AND je.entryDate <= :asOfDate")
    BigDecimal calculateAccountBalance(
        @Param("accountId") UUID accountId,
        @Param("asOfDate") LocalDateTime asOfDate);

    /**
     * Find unposted entries
     */
    List<JournalEntry> findByStatus(JournalEntry.EntryStatus status);

    /**
     * Find entries for reconciliation
     */
    @Query("SELECT je FROM JournalEntry je WHERE " +
           "je.account.id = :accountId " +
           "AND je.status = 'POSTED' " +
           "AND je.entryDate BETWEEN :startDate AND :endDate " +
           "AND (je.metadata IS NULL OR je.metadata NOT LIKE '%reconciled%')")
    List<JournalEntry> findUnreconciledEntries(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Update entry status
     */
    @Modifying
    @Query("UPDATE JournalEntry je SET je.status = :newStatus " +
           "WHERE je.transactionId = :transactionId")
    void updateStatusByTransaction(
        @Param("transactionId") UUID transactionId,
        @Param("newStatus") JournalEntry.EntryStatus newStatus);

    /**
     * Mark entries as reversed
     */
    @Modifying
    @Query("UPDATE JournalEntry je SET " +
           "je.status = 'REVERSED', " +
           "je.reversalEntryId = :reversalEntryId " +
           "WHERE je.transactionId = :transactionId")
    void markEntriesAsReversed(
        @Param("transactionId") UUID transactionId,
        @Param("reversalEntryId") UUID reversalEntryId);

    /**
     * Get trial balance
     */
    @Query("SELECT " +
           "je.account.accountType, " +
           "je.account.accountName, " +
           "SUM(CASE WHEN je.entryType = 'DEBIT' THEN je.amount ELSE 0 END) as totalDebits, " +
           "SUM(CASE WHEN je.entryType = 'CREDIT' THEN je.amount ELSE 0 END) as totalCredits " +
           "FROM JournalEntry je " +
           "WHERE je.status = 'POSTED' " +
           "AND je.entryDate BETWEEN :startDate AND :endDate " +
           "GROUP BY je.account.accountType, je.account.accountName")
    List<Object[]> generateTrialBalance(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Find duplicate entries
     */
    @Query("SELECT je FROM JournalEntry je WHERE " +
           "EXISTS (SELECT 1 FROM JournalEntry je2 " +
           "WHERE je2.id != je.id " +
           "AND je2.account.id = je.account.id " +
           "AND je2.amount = je.amount " +
           "AND je2.entryType = je.entryType " +
           "AND ABS(TIMESTAMPDIFF(SECOND, je.entryDate, je2.entryDate)) < 60)")
    List<JournalEntry> findPotentialDuplicates();

    /**
     * Count entries by status for a transaction
     */
    @Query("SELECT je.status, COUNT(je) FROM JournalEntry je " +
           "WHERE je.transactionId = :transactionId " +
           "GROUP BY je.status")
    List<Object[]> countEntriesByStatus(@Param("transactionId") UUID transactionId);
}