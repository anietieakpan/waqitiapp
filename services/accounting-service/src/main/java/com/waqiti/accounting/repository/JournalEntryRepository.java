package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.JournalEntry;
import com.waqiti.accounting.domain.JournalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Journal Entry operations
 */
@Repository
public interface JournalEntryRepository extends CrudRepository<JournalEntry, String>,
                                               PagingAndSortingRepository<JournalEntry, String> {

    /**
     * Find journal entry by transaction ID
     */
    Optional<JournalEntry> findByTransactionId(String transactionId);

    /**
     * Check if transaction has already been processed
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Find entries by status
     */
    List<JournalEntry> findByStatus(JournalStatus status);

    /**
     * Find entries by date range
     */
    List<JournalEntry> findByEntryDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find entries by financial period
     */
    List<JournalEntry> findByPeriodId(String periodId);

    /**
     * Find entries by currency
     */
    List<JournalEntry> findByCurrency(String currency);

    /**
     * Find entries created by user
     */
    List<JournalEntry> findByCreatedBy(String createdBy);

    /**
     * Search entries by description - FIXED SQL injection vulnerability
     * Uses proper escaping of wildcards to prevent SQL injection through search terms
     */
    @Query("SELECT j FROM JournalEntry j WHERE LOWER(j.description) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:searchTerm, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%'))")
    Page<JournalEntry> searchByDescription(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find entries requiring posting
     */
    List<JournalEntry> findByStatusAndCreatedAtBefore(JournalStatus status, LocalDateTime cutoffTime);

    /**
     * Count entries by status and period
     */
    long countByStatusAndPeriodId(JournalStatus status, String periodId);

    /**
     * Find unbalanced entries (for error checking)
     */
    @Query("SELECT * FROM journal_entries WHERE total_debits != total_credits")
    List<JournalEntry> findUnbalancedEntries();

    /**
     * Get entry statistics by date range
     */
    @Query("SELECT " +
           "  COUNT(*) as entry_count, " +
           "  SUM(total_debits) as total_debits, " +
           "  SUM(total_credits) as total_credits " +
           "FROM journal_entries " +
           "WHERE entry_date BETWEEN :startDate AND :endDate " +
           "AND status = 'POSTED'")
    EntryStatistics getStatistics(@Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);

    /**
     * Find large entries above threshold
     */
    @Query("SELECT * FROM journal_entries WHERE total_debits >= :threshold ORDER BY total_debits DESC")
    Page<JournalEntry> findLargeEntries(@Param("threshold") java.math.BigDecimal threshold, 
                                       Pageable pageable);

    /**
     * Data Transfer Object for statistics
     */
    interface EntryStatistics {
        Long getEntryCount();
        java.math.BigDecimal getTotalDebits();
        java.math.BigDecimal getTotalCredits();
    }
}