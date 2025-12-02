package com.waqiti.investment.repository;

import com.waqiti.investment.domain.AutoInvest;
import com.waqiti.investment.domain.enums.AutoInvestFrequency;
import com.waqiti.investment.domain.enums.AutoInvestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AutoInvestRepository extends JpaRepository<AutoInvest, String> {

    List<AutoInvest> findByInvestmentAccountId(String investmentAccountId);

    List<AutoInvest> findByInvestmentAccountIdAndStatus(String investmentAccountId, AutoInvestStatus status);

    List<AutoInvest> findByStatus(AutoInvestStatus status);

    @Query("SELECT a FROM AutoInvest a WHERE a.status = :status AND a.nextExecutionDate <= :now")
    List<AutoInvest> findDueForExecution(@Param("status") AutoInvestStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT a FROM AutoInvest a WHERE a.status = 'ACTIVE' AND a.nextExecutionDate IS NULL")
    List<AutoInvest> findActiveWithoutNextExecution();

    @Query("SELECT a FROM AutoInvest a WHERE a.investmentAccountId = :accountId " +
           "AND a.status IN ('ACTIVE', 'PAUSED')")
    List<AutoInvest> findModifiablePlans(@Param("accountId") String accountId);

    @Query("SELECT COUNT(a) FROM AutoInvest a WHERE a.status = :status")
    long countByStatus(@Param("status") AutoInvestStatus status);

    @Query("SELECT SUM(a.amount) FROM AutoInvest a WHERE a.status = 'ACTIVE'")
    BigDecimal getTotalActiveInvestmentAmount();

    @Query("SELECT SUM(a.totalInvested) FROM AutoInvest a")
    BigDecimal getTotalInvestedAmount();

    @Query("SELECT a.frequency, COUNT(a), SUM(a.amount) FROM AutoInvest a " +
           "WHERE a.status = 'ACTIVE' GROUP BY a.frequency")
    List<Object[]> getActiveInvestmentsByFrequency();

    @Query("SELECT a FROM AutoInvest a WHERE a.failedCount > :threshold " +
           "AND a.status = 'ACTIVE'")
    List<AutoInvest> findProblematicPlans(@Param("threshold") Integer threshold);

    @Query("SELECT a FROM AutoInvest a WHERE a.endDate < CURRENT_DATE " +
           "AND a.status = 'ACTIVE'")
    List<AutoInvest> findExpiredActivePlans();

    @Query("SELECT DATE(a.lastExecutionDate), COUNT(a), SUM(a.amount) FROM AutoInvest a " +
           "WHERE a.lastExecutionDate >= :startDate AND a.lastExecutionStatus = 'SUCCESS' " +
           "GROUP BY DATE(a.lastExecutionDate)")
    List<Object[]> getDailyExecutionStats(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT a FROM AutoInvest a WHERE a.rebalanceEnabled = true " +
           "AND a.status = 'ACTIVE'")
    List<AutoInvest> findRebalancingEnabledPlans();

    Optional<AutoInvest> findByInvestmentAccountIdAndPlanName(String investmentAccountId, String planName);

    @Query("SELECT AVG(a.executionCount) FROM AutoInvest a WHERE a.status IN ('ACTIVE', 'COMPLETED')")
    Double getAverageExecutionCount();

    @Query("SELECT a FROM AutoInvest a JOIN FETCH a.allocations WHERE a.id = :id")
    Optional<AutoInvest> findByIdWithAllocations(@Param("id") String id);

    @Query("SELECT DISTINCT a.investmentAccountId FROM AutoInvest a WHERE a.status = 'ACTIVE'")
    List<String> findAccountsWithActivePlans();

    @Query("SELECT a FROM AutoInvest a WHERE a.lastExecutionDate < :date " +
           "AND a.status = 'ACTIVE'")
    List<AutoInvest> findInactivePlans(@Param("date") LocalDateTime date);
}