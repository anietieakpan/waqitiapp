package com.waqiti.investment.repository;

import com.waqiti.investment.domain.InvestmentAccount;
import com.waqiti.investment.domain.enums.AccountStatus;
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
import java.util.Optional;

@Repository
public interface InvestmentAccountRepository extends JpaRepository<InvestmentAccount, String> {

    Optional<InvestmentAccount> findByCustomerId(String customerId);

    Optional<InvestmentAccount> findByAccountNumber(String accountNumber);

    Optional<InvestmentAccount> findByCustomerIdAndStatus(String customerId, AccountStatus status);

    boolean existsByCustomerId(String customerId);

    boolean existsByAccountNumber(String accountNumber);

    List<InvestmentAccount> findByStatus(AccountStatus status);

    Page<InvestmentAccount> findByStatus(AccountStatus status, Pageable pageable);

    @Query("SELECT ia FROM InvestmentAccount ia WHERE ia.status = :status AND ia.kycVerified = true")
    List<InvestmentAccount> findActiveAndVerifiedAccounts(@Param("status") AccountStatus status);

    @Query("SELECT ia FROM InvestmentAccount ia WHERE ia.totalValue > :minValue ORDER BY ia.totalValue DESC")
    List<InvestmentAccount> findHighValueAccounts(@Param("minValue") BigDecimal minValue);

    @Query("SELECT ia FROM InvestmentAccount ia WHERE ia.patternDayTrader = true")
    List<InvestmentAccount> findPatternDayTraders();

    @Query("SELECT COUNT(ia) FROM InvestmentAccount ia WHERE ia.status = :status")
    long countByStatus(@Param("status") AccountStatus status);

    @Query("SELECT SUM(ia.totalValue) FROM InvestmentAccount ia WHERE ia.status = :status")
    BigDecimal getTotalValueByStatus(@Param("status") AccountStatus status);

    @Query("SELECT AVG(ia.totalValue) FROM InvestmentAccount ia WHERE ia.status = :status")
    BigDecimal getAverageAccountValue(@Param("status") AccountStatus status);

    @Query("SELECT ia FROM InvestmentAccount ia WHERE ia.lastActivityAt < :date AND ia.status = :status")
    List<InvestmentAccount> findInactiveAccounts(@Param("date") LocalDateTime date, @Param("status") AccountStatus status);

    @Query("SELECT ia FROM InvestmentAccount ia WHERE ia.cashBalance > :minCash AND ia.status = :status")
    List<InvestmentAccount> findAccountsWithCash(@Param("minCash") BigDecimal minCash, @Param("status") AccountStatus status);

    @Modifying
    @Query("UPDATE InvestmentAccount ia SET ia.dayTrades = 0 WHERE ia.status = :status")
    void resetDayTrades(@Param("status") AccountStatus status);

    @Query("SELECT ia FROM InvestmentAccount ia WHERE ia.riskProfile = :riskProfile")
    List<InvestmentAccount> findByRiskProfile(@Param("riskProfile") String riskProfile);

    @Query("SELECT ia FROM InvestmentAccount ia WHERE ia.totalReturn > :minReturn")
    List<InvestmentAccount> findProfitableAccounts(@Param("minReturn") BigDecimal minReturn);

    @Query("SELECT ia FROM InvestmentAccount ia WHERE ia.unrealizedGains < 0")
    List<InvestmentAccount> findAccountsWithUnrealizedLosses();

    @Query(value = "SELECT * FROM investment_accounts WHERE metadata @> :metadata", nativeQuery = true)
    List<InvestmentAccount> findByMetadata(@Param("metadata") String metadata);

    @Query("SELECT ia.brokerageProvider, COUNT(ia) FROM InvestmentAccount ia GROUP BY ia.brokerageProvider")
    List<Object[]> countByBrokerageProvider();

    @Query("SELECT DATE(ia.createdAt), COUNT(ia) FROM InvestmentAccount ia " +
           "WHERE ia.createdAt >= :startDate GROUP BY DATE(ia.createdAt) ORDER BY DATE(ia.createdAt)")
    List<Object[]> getDailyAccountCreations(@Param("startDate") LocalDateTime startDate);
}