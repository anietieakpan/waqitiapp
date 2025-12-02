package com.waqiti.investment.repository;

import com.waqiti.investment.domain.InvestmentHolding;
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
public interface InvestmentHoldingRepository extends JpaRepository<InvestmentHolding, String> {

    Optional<InvestmentHolding> findByInvestmentAccountIdAndSymbol(String investmentAccountId, String symbol);

    List<InvestmentHolding> findByInvestmentAccountId(String investmentAccountId);

    Page<InvestmentHolding> findByInvestmentAccountId(String investmentAccountId, Pageable pageable);

    List<InvestmentHolding> findByPortfolioId(String portfolioId);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.investmentAccount.id = :accountId " +
           "AND h.quantity > 0 ORDER BY h.marketValue DESC")
    List<InvestmentHolding> findActiveHoldings(@Param("accountId") String accountId);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.symbol = :symbol AND h.quantity > 0")
    List<InvestmentHolding> findAllHoldingsBySymbol(@Param("symbol") String symbol);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.unrealizedGains > 0 " +
           "ORDER BY h.totalReturnPercent DESC")
    List<InvestmentHolding> findProfitableHoldings();

    @Query("SELECT h FROM InvestmentHolding h WHERE h.unrealizedGains < 0 " +
           "ORDER BY h.totalReturnPercent ASC")
    List<InvestmentHolding> findLossHoldings();

    @Query("SELECT h FROM InvestmentHolding h WHERE h.totalReturnPercent > :minReturn " +
           "ORDER BY h.totalReturnPercent DESC")
    List<InvestmentHolding> findTopPerformers(@Param("minReturn") BigDecimal minReturn);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.dayChangePercent < :threshold")
    List<InvestmentHolding> findHoldingsWithLargeDrops(@Param("threshold") BigDecimal threshold);

    @Query("SELECT h.symbol, SUM(h.quantity), AVG(h.averageCost), SUM(h.marketValue) " +
           "FROM InvestmentHolding h WHERE h.quantity > 0 GROUP BY h.symbol")
    List<Object[]> getAggregatedHoldingsBySymbol();

    @Query("SELECT h.instrumentType, COUNT(h), SUM(h.marketValue) FROM InvestmentHolding h " +
           "WHERE h.quantity > 0 GROUP BY h.instrumentType")
    List<Object[]> getHoldingsByInstrumentType();

    @Query("SELECT SUM(h.marketValue) FROM InvestmentHolding h WHERE h.investmentAccount.id = :accountId " +
           "AND h.quantity > 0")
    BigDecimal getTotalPortfolioValue(@Param("accountId") String accountId);

    @Query("SELECT SUM(h.unrealizedGains) FROM InvestmentHolding h WHERE h.investmentAccount.id = :accountId")
    BigDecimal getTotalUnrealizedGains(@Param("accountId") String accountId);

    @Query("SELECT SUM(h.dividendEarnings) FROM InvestmentHolding h WHERE h.investmentAccount.id = :accountId")
    BigDecimal getTotalDividendEarnings(@Param("accountId") String accountId);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.lastPriceUpdate < :threshold")
    List<InvestmentHolding> findStaleHoldings(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.portfolioPercentage > :maxPercent")
    List<InvestmentHolding> findConcentratedPositions(@Param("maxPercent") BigDecimal maxPercent);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.marketCap < :maxCap AND h.quantity > 0")
    List<InvestmentHolding> findSmallCapHoldings(@Param("maxCap") BigDecimal maxCap);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.dividendYield > :minYield " +
           "AND h.quantity > 0 ORDER BY h.dividendYield DESC")
    List<InvestmentHolding> findHighYieldHoldings(@Param("minYield") BigDecimal minYield);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.beta > :maxBeta AND h.quantity > 0")
    List<InvestmentHolding> findHighBetaHoldings(@Param("maxBeta") BigDecimal maxBeta);

    @Modifying
    @Query("UPDATE InvestmentHolding h SET h.currentPrice = :price, h.lastPriceUpdate = :updateTime " +
           "WHERE h.symbol = :symbol")
    void updatePriceForSymbol(@Param("symbol") String symbol, 
                             @Param("price") BigDecimal price, 
                             @Param("updateTime") LocalDateTime updateTime);

    @Query("SELECT COUNT(DISTINCT h.symbol) FROM InvestmentHolding h " +
           "WHERE h.investmentAccount.id = :accountId AND h.quantity > 0")
    long countUniqueHoldings(@Param("accountId") String accountId);

    @Query("SELECT h FROM InvestmentHolding h WHERE h.currentPrice >= h.fiftyTwoWeekHigh * 0.95 " +
           "AND h.quantity > 0")
    List<InvestmentHolding> findNearFiftyTwoWeekHighs();

    @Query("SELECT h FROM InvestmentHolding h WHERE h.currentPrice <= h.fiftyTwoWeekLow * 1.05 " +
           "AND h.quantity > 0")
    List<InvestmentHolding> findNearFiftyTwoWeekLows();
}