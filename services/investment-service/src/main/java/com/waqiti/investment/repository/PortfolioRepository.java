package com.waqiti.investment.repository;

import com.waqiti.investment.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, String> {

    Optional<Portfolio> findByInvestmentAccountId(String investmentAccountId);

    @Query("SELECT p FROM Portfolio p WHERE p.totalValue > :minValue ORDER BY p.totalValue DESC")
    List<Portfolio> findHighValuePortfolios(@Param("minValue") BigDecimal minValue);

    @Query("SELECT p FROM Portfolio p WHERE p.totalReturnPercent > :minReturn ORDER BY p.totalReturnPercent DESC")
    List<Portfolio> findTopPerformingPortfolios(@Param("minReturn") BigDecimal minReturn);

    @Query("SELECT p FROM Portfolio p WHERE p.totalReturnPercent < 0 ORDER BY p.totalReturnPercent ASC")
    List<Portfolio> findLossPortfolios();

    @Query("SELECT p FROM Portfolio p WHERE p.diversificationScore > :minScore")
    List<Portfolio> findWellDiversifiedPortfolios(@Param("minScore") BigDecimal minScore);

    @Query("SELECT p FROM Portfolio p WHERE p.riskScore > :maxRisk")
    List<Portfolio> findHighRiskPortfolios(@Param("maxRisk") BigDecimal maxRisk);

    @Query("SELECT AVG(p.totalValue) FROM Portfolio p")
    BigDecimal getAveragePortfolioValue();

    @Query("SELECT AVG(p.totalReturnPercent) FROM Portfolio p")
    BigDecimal getAverageReturnPercent();

    @Query("SELECT SUM(p.totalValue) FROM Portfolio p")
    BigDecimal getTotalAssetsUnderManagement();

    @Query("SELECT COUNT(p) FROM Portfolio p WHERE p.numberOfPositions > :minPositions")
    long countActivePortfolios(@Param("minPositions") Integer minPositions);

    @Query("SELECT p FROM Portfolio p WHERE p.cashPercentage > :minCashPercent")
    List<Portfolio> findCashHeavyPortfolios(@Param("minCashPercent") BigDecimal minCashPercent);

    @Query("SELECT p FROM Portfolio p WHERE p.lastRebalancedAt < :date OR p.lastRebalancedAt IS NULL")
    List<Portfolio> findPortfoliosNeedingRebalancing(@Param("date") LocalDateTime date);

    @Query("SELECT p.topPerformer, COUNT(p) FROM Portfolio p WHERE p.topPerformer IS NOT NULL " +
           "GROUP BY p.topPerformer ORDER BY COUNT(p) DESC")
    List<Object[]> getMostFrequentTopPerformers();

    @Query("SELECT p FROM Portfolio p WHERE p.volatility > :maxVolatility")
    List<Portfolio> findVolatilePortfolios(@Param("maxVolatility") BigDecimal maxVolatility);

    @Query("SELECT p FROM Portfolio p WHERE p.sharpeRatio > :minSharpe ORDER BY p.sharpeRatio DESC")
    List<Portfolio> findEfficientPortfolios(@Param("minSharpe") BigDecimal minSharpe);

    @Query("SELECT AVG(p.equityPercentage), AVG(p.etfPercentage), AVG(p.cryptoPercentage), AVG(p.cashPercentage) FROM Portfolio p")
    Object getAverageAssetAllocation();

    @Query("SELECT p FROM Portfolio p WHERE p.dayChangePercent < :threshold")
    List<Portfolio> findPortfoliosWithLargeDrops(@Param("threshold") BigDecimal threshold);

    @Query("SELECT DATE(p.createdAt), COUNT(p), AVG(p.totalValue) FROM Portfolio p " +
           "WHERE p.createdAt >= :startDate GROUP BY DATE(p.createdAt)")
    List<Object[]> getDailyPortfolioStats(@Param("startDate") LocalDateTime startDate);
    
    // ===============================================
    // N+1 QUERY OPTIMIZATION METHODS - INVESTMENT PERFORMANCE
    // ===============================================
    
    /**
     * N+1 QUERY FIX: Find portfolio with holdings loaded - critical performance fix
     * Portfolio → Holdings is the most common N+1 pattern in investment service
     */
    @Query("SELECT DISTINCT p FROM Portfolio p " +
           "LEFT JOIN FETCH p.holdings h " +
           "WHERE p.investmentAccount.id = :accountId")
    Optional<Portfolio> findByInvestmentAccountIdWithHoldings(@Param("accountId") String accountId);
    
    /**
     * N+1 QUERY FIX: Find portfolios with all related data for comprehensive view
     * Prevents multiple round trips when building portfolio responses
     */
    @Query("SELECT DISTINCT p FROM Portfolio p " +
           "LEFT JOIN FETCH p.holdings h " +
           "LEFT JOIN FETCH p.investmentAccount a " +
           "WHERE p.id IN :portfolioIds")
    List<Portfolio> findByIdsWithAllRelations(@Param("portfolioIds") List<String> portfolioIds);
    
    /**
     * N+1 QUERY FIX: Get portfolio summaries without lazy loading - projection query
     * Returns only essential data for portfolio lists, avoiding entity graph overhead
     */
    @Query("SELECT p.id, p.totalValue, p.totalReturn, p.totalReturnPercent, " +
           "p.dayChange, p.dayChangePercent, p.numberOfPositions, " +
           "p.diversificationScore, p.riskScore, p.createdAt " +
           "FROM Portfolio p " +
           "WHERE p.investmentAccount.id IN :accountIds " +
           "ORDER BY p.createdAt DESC")
    List<Object[]> findPortfolioSummariesByAccountIds(@Param("accountIds") List<String> accountIds);
    
    /**
     * N+1 QUERY FIX: Get portfolio performance metrics in single aggregation query
     * Optimized for performance dashboards without loading full entities
     */
    @Query("SELECT " +
           "COUNT(p) as totalPortfolios, " +
           "SUM(p.totalValue) as totalValue, " +
           "AVG(p.totalReturnPercent) as avgReturn, " +
           "AVG(p.riskScore) as avgRisk, " +
           "AVG(p.diversificationScore) as avgDiversification, " +
           "COUNT(CASE WHEN p.totalReturnPercent > 0 THEN 1 END) as profitableCount " +
           "FROM Portfolio p " +
           "WHERE p.investmentAccount.userId IN :userIds")
    Object[] getPortfolioStatisticsByUserIds(@Param("userIds") List<String> userIds);
    
    /**
     * N+1 QUERY FIX: Batch update portfolio metrics in single transaction
     * Critical for market data synchronization operations
     */
    @Modifying
    @Query("UPDATE Portfolio p SET " +
           "p.totalValue = " +
           "    CASE " +
           "        WHEN p.id = :portfolioId1 THEN :totalValue1 " +
           "        WHEN p.id = :portfolioId2 THEN :totalValue2 " +
           "        ELSE p.totalValue " +
           "    END, " +
           "p.dayChange = " +
           "    CASE " +
           "        WHEN p.id = :portfolioId1 THEN :dayChange1 " +
           "        WHEN p.id = :portfolioId2 THEN :dayChange2 " +
           "        ELSE p.dayChange " +
           "    END, " +
           "p.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE p.id IN (:portfolioId1, :portfolioId2)")
    int bulkUpdatePortfolioMetrics(@Param("portfolioId1") String portfolioId1, 
                                  @Param("totalValue1") BigDecimal totalValue1,
                                  @Param("dayChange1") BigDecimal dayChange1,
                                  @Param("portfolioId2") String portfolioId2,
                                  @Param("totalValue2") BigDecimal totalValue2,
                                  @Param("dayChange2") BigDecimal dayChange2);
}