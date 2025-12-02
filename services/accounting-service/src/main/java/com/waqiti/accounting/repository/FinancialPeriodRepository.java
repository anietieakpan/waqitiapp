package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.FinancialPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Financial Period Repository
 * Repository for managing fiscal periods
 */
@Repository
public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, UUID> {

    /**
     * Find the current open financial period
     */
    @Query("SELECT fp FROM FinancialPeriod fp WHERE fp.isClosed = false " +
           "AND :currentDate BETWEEN fp.startDate AND fp.endDate")
    Optional<FinancialPeriod> findCurrentPeriod(@Param("currentDate") LocalDate currentDate);

    /**
     * Find period by fiscal year and period number
     */
    Optional<FinancialPeriod> findByFiscalYearAndPeriodNumber(Integer fiscalYear, Integer periodNumber);

    /**
     * Find period containing a specific date
     */
    @Query("SELECT fp FROM FinancialPeriod fp WHERE :date BETWEEN fp.startDate AND fp.endDate")
    Optional<FinancialPeriod> findByDate(@Param("date") LocalDate date);

    /**
     * Find all periods for a fiscal year
     */
    List<FinancialPeriod> findByFiscalYearOrderByPeriodNumberAsc(Integer fiscalYear);

    /**
     * Find open (unclosed) periods
     */
    List<FinancialPeriod> findByIsClosedFalse();

    /**
     * Find periods within date range
     */
    @Query("SELECT fp FROM FinancialPeriod fp WHERE fp.startDate >= :startDate AND fp.endDate <= :endDate " +
           "ORDER BY fp.startDate ASC")
    List<FinancialPeriod> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * Find the latest closed period
     */
    @Query("SELECT fp FROM FinancialPeriod fp WHERE fp.isClosed = true " +
           "ORDER BY fp.endDate DESC")
    Optional<FinancialPeriod> findLatestClosedPeriod();

    /**
     * Check if period exists
     */
    boolean existsByFiscalYearAndPeriodNumber(Integer fiscalYear, Integer periodNumber);
}
