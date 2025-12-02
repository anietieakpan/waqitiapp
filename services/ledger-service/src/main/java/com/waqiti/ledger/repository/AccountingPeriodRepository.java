package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AccountingPeriod entities
 */
@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {

    /**
     * Find period by code
     */
    Optional<AccountingPeriod> findByPeriodCode(String periodCode);

    /**
     * Check if period code exists
     */
    boolean existsByPeriodCode(String periodCode);

    /**
     * Find period containing specific date
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE :date BETWEEN ap.startDate AND ap.endDate")
    Optional<AccountingPeriod> findPeriodContainingDate(@Param("date") LocalDate date);

    /**
     * Find current period
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE CURRENT_DATE BETWEEN ap.startDate AND ap.endDate")
    Optional<AccountingPeriod> findCurrentPeriod();

    /**
     * Find periods by status
     */
    List<AccountingPeriod> findByStatusOrderByStartDateDesc(AccountingPeriod.PeriodStatus status);

    /**
     * Find periods by type
     */
    List<AccountingPeriod> findByPeriodTypeOrderByStartDateDesc(AccountingPeriod.PeriodType periodType);

    /**
     * Find periods by fiscal year
     */
    List<AccountingPeriod> findByFiscalYearOrderByStartDateAsc(Integer fiscalYear);

    /**
     * Find periods in date range
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE " +
           "ap.startDate <= :endDate AND ap.endDate >= :startDate " +
           "ORDER BY ap.startDate ASC")
    List<AccountingPeriod> findPeriodsInDateRange(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * Find open periods
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.status IN ('OPEN', 'REOPENED') ORDER BY ap.startDate ASC")
    List<AccountingPeriod> findOpenPeriods();

    /**
     * Find closed periods
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.status IN ('SOFT_CLOSED', 'HARD_CLOSED', 'LOCKED') ORDER BY ap.startDate DESC")
    List<AccountingPeriod> findClosedPeriods();

    /**
     * Find periods that can be closed
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.status = 'OPEN' AND ap.endDate < CURRENT_DATE")
    List<AccountingPeriod> findPeriodsReadyForClose();

    /**
     * Find overlapping periods
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE " +
           "ap.periodId != :periodId AND " +
           "ap.startDate <= :endDate AND ap.endDate >= :startDate")
    List<AccountingPeriod> findOverlappingPeriods(@Param("periodId") UUID periodId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * Find periods by fiscal quarter
     */
    List<AccountingPeriod> findByFiscalYearAndFiscalQuarterOrderByStartDateAsc(Integer fiscalYear, Integer fiscalQuarter);

    /**
     * Find periods by fiscal month
     */
    List<AccountingPeriod> findByFiscalYearAndFiscalMonthOrderByStartDateAsc(Integer fiscalYear, Integer fiscalMonth);

    /**
     * Find adjustment periods
     */
    List<AccountingPeriod> findByIsAdjustmentPeriodTrueOrderByStartDateDesc(Boolean isAdjustmentPeriod);

    /**
     * Find periods by parent period
     */
    List<AccountingPeriod> findByParentPeriodIdOrderByStartDateAsc(UUID parentPeriodId);

    /**
     * Get latest closed period
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.status IN ('SOFT_CLOSED', 'HARD_CLOSED', 'LOCKED') " +
           "ORDER BY ap.endDate DESC LIMIT 1")
    Optional<AccountingPeriod> findLatestClosedPeriod();

    /**
     * Get earliest open period
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.status IN ('OPEN', 'REOPENED') " +
           "ORDER BY ap.startDate ASC LIMIT 1")
    Optional<AccountingPeriod> findEarliestOpenPeriod();

    /**
     * Find periods that need attention (overdue for closing)
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.status = 'OPEN' " +
           "AND ap.endDate < :cutoffDate ORDER BY ap.endDate ASC")
    List<AccountingPeriod> findPeriodsNeedingAttention(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Get fiscal year range
     */
    @Query("SELECT MIN(ap.fiscalYear), MAX(ap.fiscalYear) FROM AccountingPeriod ap")
    Object[] getFiscalYearRange();

    /**
     * Find periods with transactions
     */
    @Query("SELECT DISTINCT ap FROM AccountingPeriod ap " +
           "JOIN JournalEntry je ON ap.periodId = je.accountingPeriodId " +
           "WHERE je.status = 'POSTED' " +
           "ORDER BY ap.startDate DESC")
    List<AccountingPeriod> findPeriodsWithTransactions();

    /**
     * Count periods by status
     */
    @Query("SELECT ap.status, COUNT(ap) FROM AccountingPeriod ap GROUP BY ap.status")
    List<Object[]> countPeriodsByStatus();

    /**
     * Find periods closed by user
     */
    List<AccountingPeriod> findByClosedByOrderByClosedAtDesc(String closedBy);

    /**
     * Find periods closed in date range
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.closedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY ap.closedAt DESC")
    List<AccountingPeriod> findPeriodsClosedBetween(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    /**
     * Find next period to close
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.status = 'OPEN' " +
           "AND ap.endDate = (SELECT MIN(ap2.endDate) FROM AccountingPeriod ap2 WHERE ap2.status = 'OPEN')")
    Optional<AccountingPeriod> findNextPeriodToClose();

    /**
     * Find periods that can be reopened
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.status IN ('SOFT_CLOSED', 'HARD_CLOSED') " +
           "AND ap.status != 'LOCKED' ORDER BY ap.endDate DESC")
    List<AccountingPeriod> findPeriodsEligibleForReopen();

    /**
     * Validate period sequence
     */
    @Query("SELECT ap FROM AccountingPeriod ap WHERE ap.periodType = :periodType " +
           "AND ap.fiscalYear = :fiscalYear ORDER BY ap.startDate ASC")
    List<AccountingPeriod> findPeriodsForSequenceValidation(@Param("periodType") AccountingPeriod.PeriodType periodType,
                                                          @Param("fiscalYear") Integer fiscalYear);

    /**
     * Get period statistics
     */
    @Query("SELECT ap.fiscalYear, ap.periodType, COUNT(ap), " +
           "SUM(CASE WHEN ap.status = 'OPEN' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN ap.status IN ('SOFT_CLOSED', 'HARD_CLOSED', 'LOCKED') THEN 1 ELSE 0 END) " +
           "FROM AccountingPeriod ap GROUP BY ap.fiscalYear, ap.periodType " +
           "ORDER BY ap.fiscalYear DESC, ap.periodType")
    List<Object[]> getPeriodStatistics();
}