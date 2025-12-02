package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for JournalEntry entities
 */
@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    /**
     * Find journal entry by entry number
     */
    Optional<JournalEntry> findByEntryNumber(String entryNumber);

    /**
     * Check if entry number exists
     */
    boolean existsByEntryNumber(String entryNumber);

    /**
     * Find journal entries by status
     */
    Page<JournalEntry> findByStatusOrderByCreatedAtDesc(JournalEntry.JournalStatus status, Pageable pageable);

    /**
     * Find journal entries by type
     */
    Page<JournalEntry> findByEntryTypeOrderByEntryDateDesc(JournalEntry.EntryType entryType, Pageable pageable);

    /**
     * Find journal entries by date range
     */
    Page<JournalEntry> findByEntryDateBetweenOrderByEntryDateDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find journal entries by reference number
     */
    List<JournalEntry> findByReferenceNumberContainingIgnoreCaseOrderByEntryDateDesc(String referenceNumber);

    /**
     * Find journal entries by accounting period
     */
    Page<JournalEntry> findByAccountingPeriodIdOrderByEntryDateDesc(UUID accountingPeriodId, Pageable pageable);

    /**
     * Find journal entries requiring approval
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.approvalRequired = true AND je.approvedAt IS NULL AND je.status = 'PENDING_APPROVAL'")
    Page<JournalEntry> findEntriesRequiringApproval(Pageable pageable);

    /**
     * Find posted journal entries
     */
    Page<JournalEntry> findByStatusAndPostedAtIsNotNullOrderByPostedAtDesc(
            JournalEntry.JournalStatus status, Pageable pageable);

    /**
     * Find draft entries by user
     */
    Page<JournalEntry> findByStatusAndCreatedByOrderByCreatedAtDesc(
            JournalEntry.JournalStatus status, String createdBy, Pageable pageable);

    /**
     * Find entries that can be reversed
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.status = 'POSTED' AND je.reversedAt IS NULL")
    Page<JournalEntry> findReversibleEntries(Pageable pageable);

    /**
     * Find reversal entries for original entry
     */
    List<JournalEntry> findByOriginalJournalEntryIdOrderByCreatedAtDesc(UUID originalJournalEntryId);

    /**
     * Complex search with multiple criteria
     */
    @Query("SELECT je FROM JournalEntry je WHERE " +
           "(:entryType IS NULL OR je.entryType = :entryType) AND " +
           "(:status IS NULL OR je.status = :status) AND " +
           "(:startDate IS NULL OR je.entryDate >= :startDate) AND " +
           "(:endDate IS NULL OR je.entryDate <= :endDate) AND " +
           "(:reference IS NULL OR LOWER(je.referenceNumber) LIKE LOWER(CONCAT('%', :reference, '%'))) AND " +
           "(:description IS NULL OR LOWER(je.description) LIKE LOWER(CONCAT('%', :description, '%'))) " +
           "ORDER BY je.entryDate DESC")
    Page<JournalEntry> searchJournalEntries(
            @Param("entryType") JournalEntry.EntryType entryType,
            @Param("status") JournalEntry.JournalStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("reference") String reference,
            @Param("description") String description,
            Pageable pageable);

    /**
     * Find entries by source system
     */
    Page<JournalEntry> findBySourceSystemOrderByCreatedAtDesc(String sourceSystem, Pageable pageable);

    /**
     * Find entries by source document
     */
    List<JournalEntry> findBySourceDocumentIdAndSourceDocumentTypeOrderByCreatedAtDesc(
            String sourceDocumentId, String sourceDocumentType);

    /**
     * Get entries summary by period
     */
    @Query("SELECT je.entryType, COUNT(je), SUM(je.totalDebits), SUM(je.totalCredits) " +
           "FROM JournalEntry je WHERE je.entryDate BETWEEN :startDate AND :endDate " +
           "GROUP BY je.entryType")
    List<Object[]> getEntriesSummaryByPeriod(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Get entries count by status
     */
    @Query("SELECT je.status, COUNT(je) FROM JournalEntry je GROUP BY je.status")
    List<Object[]> getEntriesCountByStatus();

    /**
     * Find unbalanced entries
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.totalDebits != je.totalCredits")
    List<JournalEntry> findUnbalancedEntries();

    /**
     * Find entries with specific total amount
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.totalDebits = :amount OR je.totalCredits = :amount")
    List<JournalEntry> findEntriesByAmount(@Param("amount") java.math.BigDecimal amount);

    /**
     * Find entries posted in date range
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.postedAt BETWEEN :startDate AND :endDate ORDER BY je.postedAt DESC")
    Page<JournalEntry> findEntriesPostedBetween(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate,
                                              Pageable pageable);

    /**
     * Get total amounts by currency
     */
    @Query("SELECT je.currency, SUM(je.totalDebits), SUM(je.totalCredits) " +
           "FROM JournalEntry je WHERE je.status = 'POSTED' AND je.entryDate BETWEEN :startDate AND :endDate " +
           "GROUP BY je.currency")
    List<Object[]> getTotalAmountsByCurrency(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Find entries requiring period-end processing
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.entryType IN ('ADJUSTING', 'CLOSING', 'ACCRUAL') " +
           "AND je.status = 'POSTED' AND je.entryDate BETWEEN :startDate AND :endDate")
    List<JournalEntry> findPeriodEndEntries(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    /**
     * Find entries by user and date range
     */
    @Query("SELECT je FROM JournalEntry je WHERE " +
           "(je.createdBy = :user OR je.postedBy = :user OR je.approvedBy = :user) " +
           "AND je.entryDate BETWEEN :startDate AND :endDate " +
           "ORDER BY je.entryDate DESC")
    Page<JournalEntry> findEntriesByUserAndDateRange(@Param("user") String user,
                                                   @Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate,
                                                   Pageable pageable);

    /**
     * Count entries by type and status
     */
    @Query("SELECT je.entryType, je.status, COUNT(je) " +
           "FROM JournalEntry je " +
           "GROUP BY je.entryType, je.status")
    List<Object[]> countEntriesByTypeAndStatus();

    /**
     * Find entries with large amounts (over threshold)
     */
    @Query("SELECT je FROM JournalEntry je WHERE " +
           "(je.totalDebits > :threshold OR je.totalCredits > :threshold) " +
           "AND je.status = 'POSTED' " +
           "ORDER BY GREATEST(je.totalDebits, je.totalCredits) DESC")
    Page<JournalEntry> findLargeAmountEntries(@Param("threshold") java.math.BigDecimal threshold,
                                            Pageable pageable);

    /**
     * Get next entry number sequence
     */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(je.entryNumber, 3) AS integer)), 0) + 1 " +
           "FROM JournalEntry je WHERE je.entryNumber LIKE 'JE%'")
    Integer getNextEntryNumber();

    /**
     * Find entries in specific accounting period
     */
    @Query("SELECT je FROM JournalEntry je " +
           "JOIN AccountingPeriod ap ON je.accountingPeriodId = ap.periodId " +
           "WHERE ap.periodCode = :periodCode " +
           "ORDER BY je.entryDate DESC")
    Page<JournalEntry> findEntriesInPeriod(@Param("periodCode") String periodCode, Pageable pageable);

    /**
     * CRITICAL METHOD - Check if there are pending (non-posted) journal entries for an account
     * Used by LedgerServiceImpl.getWalletBalance() to determine if balance calculation is final
     *
     * Pending entries include:
     * - DRAFT - Not yet submitted
     * - PENDING_APPROVAL - Awaiting approval
     * - Any entry where status is not POSTED or REJECTED
     *
     * @param accountId The ledger account ID to check
     * @return true if there are pending entries affecting this account, false otherwise
     */
    @Query("SELECT COUNT(je) > 0 FROM JournalEntry je " +
           "JOIN LedgerEntry le ON le.journalEntryId = je.journalEntryId " +
           "WHERE le.accountId = :accountId " +
           "AND je.status IN ('DRAFT', 'PENDING_APPROVAL') " +
           "AND je.postedAt IS NULL")
    boolean existsPendingForAccount(@Param("accountId") UUID accountId);

    /**
     * Count pending entries for an account
     * Useful for detailed reporting
     *
     * @param accountId The ledger account ID
     * @return The count of pending entries
     */
    @Query("SELECT COUNT(DISTINCT je) FROM JournalEntry je " +
           "JOIN LedgerEntry le ON le.journalEntryId = je.journalEntryId " +
           "WHERE le.accountId = :accountId " +
           "AND je.status IN ('DRAFT', 'PENDING_APPROVAL')")
    long countPendingForAccount(@Param("accountId") UUID accountId);

    /**
     * Get all pending entries for an account with details
     * Useful for understanding what's preventing balance finalization
     *
     * @param accountId The ledger account ID
     * @return List of pending journal entries
     */
    @Query("SELECT DISTINCT je FROM JournalEntry je " +
           "JOIN LedgerEntry le ON le.journalEntryId = je.journalEntryId " +
           "WHERE le.accountId = :accountId " +
           "AND je.status IN ('DRAFT', 'PENDING_APPROVAL') " +
           "ORDER BY je.createdAt DESC")
    List<JournalEntry> findPendingForAccount(@Param("accountId") UUID accountId);

    /**
     * Get pending entries for an account within a date range
     * Useful for period-specific reconciliation
     *
     * @param accountId The ledger account ID
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of pending journal entries in date range
     */
    @Query("SELECT DISTINCT je FROM JournalEntry je " +
           "JOIN LedgerEntry le ON le.journalEntryId = je.journalEntryId " +
           "WHERE le.accountId = :accountId " +
           "AND je.status IN ('DRAFT', 'PENDING_APPROVAL') " +
           "AND je.entryDate BETWEEN :startDate AND :endDate " +
           "ORDER BY je.entryDate DESC")
    List<JournalEntry> findPendingForAccountInDateRange(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Check if specific wallet has any pending transactions
     * Convenience method that combines wallet lookup with pending check
     *
     * @param walletId The wallet UUID
     * @return true if wallet has pending entries
     */
    @Query("SELECT COUNT(je) > 0 FROM JournalEntry je " +
           "JOIN LedgerEntry le ON le.journalEntryId = je.journalEntryId " +
           "JOIN Account a ON a.accountId = le.accountId " +
           "WHERE a.accountCode LIKE CONCAT('WALLET-', SUBSTRING(:walletId, 1, 8), '%') " +
           "AND je.status IN ('DRAFT', 'PENDING_APPROVAL')")
    boolean existsPendingForWallet(@Param("walletId") String walletId);
}