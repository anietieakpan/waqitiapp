package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.AccountBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account Balance Repository
 * Repository for accessing account balances by period
 */
@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    /**
     * Find balance for account
     */
    Optional<AccountBalance> findByAccountCode(String accountCode);

    /**
     * Find balance with pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.accountCode = :accountCode")
    Optional<AccountBalance> findByAccountCodeForUpdate(@Param("accountCode") String accountCode);

    /**
     * Find balance for account in specific period
     */
    Optional<AccountBalance> findByAccountCodeAndFiscalYearAndFiscalPeriod(
        String accountCode, Integer fiscalYear, String fiscalPeriod);

    /**
     * Find balance with lock for period
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.accountCode = :accountCode " +
           "AND ab.fiscalYear = :fiscalYear AND ab.fiscalPeriod = :fiscalPeriod")
    Optional<AccountBalance> findByAccountCodeAndPeriodForUpdate(
        @Param("accountCode") String accountCode,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("fiscalPeriod") String fiscalPeriod);

    /**
     * Find all balances for a fiscal period
     */
    List<AccountBalance> findByFiscalYearAndFiscalPeriod(Integer fiscalYear, String fiscalPeriod);

    /**
     * Find open periods (not closed)
     */
    List<AccountBalance> findByIsClosedFalse();

    /**
     * Find balances for multiple accounts in a period
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.accountCode IN :accountCodes " +
           "AND ab.fiscalYear = :fiscalYear AND ab.fiscalPeriod = :fiscalPeriod")
    List<AccountBalance> findByAccountCodesAndPeriod(
        @Param("accountCodes") List<String> accountCodes,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("fiscalPeriod") String fiscalPeriod);

    /**
     * Check if balance exists for account and period
     */
    boolean existsByAccountCodeAndFiscalYearAndFiscalPeriod(
        String accountCode, Integer fiscalYear, String fiscalPeriod);
}
