package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.GeneralLedgerEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * General Ledger Repository
 * Repository for accessing general ledger entries
 */
@Repository
public interface GeneralLedgerRepository extends JpaRepository<GeneralLedgerEntry, UUID> {

    /**
     * Find all ledger entries for an account ordered by posting date
     */
    List<GeneralLedgerEntry> findByAccountCodeOrderByPostingDateDesc(String accountCode);

    /**
     * Find ledger entries for an account in a specific period
     */
    @Query("SELECT g FROM GeneralLedgerEntry g WHERE g.accountCode = :accountCode " +
           "AND g.fiscalYear = :fiscalYear AND g.fiscalPeriod = :fiscalPeriod " +
           "ORDER BY g.postingDate DESC")
    List<GeneralLedgerEntry> findByAccountCodeAndPeriod(
        @Param("accountCode") String accountCode,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("fiscalPeriod") String fiscalPeriod);

    /**
     * Sum debits for an account in a period
     */
    @Query("SELECT COALESCE(SUM(g.debitAmount), 0) FROM GeneralLedgerEntry g " +
           "WHERE g.accountCode = :accountCode AND g.fiscalYear = :fiscalYear " +
           "AND g.fiscalPeriod = :fiscalPeriod")
    Optional<BigDecimal> sumDebits(
        @Param("accountCode") String accountCode,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("fiscalPeriod") String fiscalPeriod);

    /**
     * Sum credits for an account in a period
     */
    @Query("SELECT COALESCE(SUM(g.creditAmount), 0) FROM GeneralLedgerEntry g " +
           "WHERE g.accountCode = :accountCode AND g.fiscalYear = :fiscalYear " +
           "AND g.fiscalPeriod = :fiscalPeriod")
    Optional<BigDecimal> sumCredits(
        @Param("accountCode") String accountCode,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("fiscalPeriod") String fiscalPeriod);

    /**
     * Sum debits for an account as of a date
     */
    @Query("SELECT COALESCE(SUM(g.debitAmount), 0) FROM GeneralLedgerEntry g " +
           "WHERE g.accountCode = :accountCode AND g.postingDate <= :asOfDate")
    Optional<BigDecimal> sumDebitsAsOf(
        @Param("accountCode") String accountCode,
        @Param("asOfDate") LocalDate asOfDate);

    /**
     * Sum credits for an account as of a date
     */
    @Query("SELECT COALESCE(SUM(g.creditAmount), 0) FROM GeneralLedgerEntry g " +
           "WHERE g.accountCode = :accountCode AND g.postingDate <= :asOfDate")
    Optional<BigDecimal> sumCreditsAsOf(
        @Param("accountCode") String accountCode,
        @Param("asOfDate") LocalDate asOfDate);

    /**
     * Find entries by journal entry ID
     */
    List<GeneralLedgerEntry> findByEntryId(String entryId);

    /**
     * Find entries with pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GeneralLedgerEntry g WHERE g.id = :id")
    Optional<GeneralLedgerEntry> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Get running balance for account as of date
     */
    @Query("SELECT g.runningBalance FROM GeneralLedgerEntry g " +
           "WHERE g.accountCode = :accountCode AND g.postingDate <= :asOfDate " +
           "ORDER BY g.postingDate DESC, g.createdAt DESC")
    Optional<BigDecimal> getRunningBalanceAsOf(
        @Param("accountCode") String accountCode,
        @Param("asOfDate") LocalDate asOfDate);

    /**
     * Sum debits for account in period by period ID
     * Joins with journal_entry to get period
     */
    @Query("SELECT COALESCE(SUM(g.debitAmount), 0) FROM GeneralLedgerEntry g " +
           "JOIN JournalEntry j ON g.entryId = j.entryId " +
           "WHERE g.accountCode = :accountCode AND j.period.id = :periodId")
    Optional<BigDecimal> sumDebitsByAccountAndPeriod(
        @Param("accountCode") String accountCode,
        @Param("periodId") String periodId);

    /**
     * Sum credits for account in period by period ID
     * Joins with journal_entry to get period
     */
    @Query("SELECT COALESCE(SUM(g.creditAmount), 0) FROM GeneralLedgerEntry g " +
           "JOIN JournalEntry j ON g.entryId = j.entryId " +
           "WHERE g.accountCode = :accountCode AND j.period.id = :periodId")
    Optional<BigDecimal> sumCreditsByAccountAndPeriod(
        @Param("accountCode") String accountCode,
        @Param("periodId") String periodId);

    /**
     * Sum debits as of date (alias for consistency)
     */
    default Optional<BigDecimal> sumDebitsAsOfDate(String accountCode, LocalDate asOfDate) {
        return sumDebitsAsOf(accountCode, asOfDate);
    }

    /**
     * Sum credits as of date (alias for consistency)
     */
    default Optional<BigDecimal> sumCreditsAsOfDate(String accountCode, LocalDate asOfDate) {
        return sumCreditsAsOf(accountCode, asOfDate);
    }
}
