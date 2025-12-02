package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.entity.ExchangeRateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Exchange Rate History
 */
@Repository
public interface ExchangeRateHistoryRepository extends JpaRepository<ExchangeRateHistory, UUID> {
    
    /**
     * Find exchange rate closest to the specified date
     */
    @Query(value = """
        SELECT * FROM exchange_rate_history 
        WHERE from_currency = :fromCurrency 
        AND to_currency = :toCurrency 
        AND rate_date <= :targetDate 
        ORDER BY rate_date DESC 
        LIMIT 1
        """, nativeQuery = true)
    Optional<ExchangeRateHistory> findClosestRateBeforeDate(
        @Param("fromCurrency") String fromCurrency,
        @Param("toCurrency") String toCurrency, 
        @Param("targetDate") LocalDateTime targetDate);
    
    /**
     * Find exchange rates for a currency pair within date range
     */
    @Query("SELECT h FROM ExchangeRateHistory h " +
           "WHERE h.fromCurrency = :fromCurrency " +
           "AND h.toCurrency = :toCurrency " +
           "AND h.rateDate BETWEEN :startDate AND :endDate " +
           "ORDER BY h.rateDate ASC")
    List<ExchangeRateHistory> findRatesInDateRange(
        @Param("fromCurrency") String fromCurrency,
        @Param("toCurrency") String toCurrency,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find the latest rate for a currency pair
     */
    Optional<ExchangeRateHistory> findTopByFromCurrencyAndToCurrencyOrderByRateDateDesc(
        String fromCurrency, String toCurrency);
    
    /**
     * Check if rate exists for specific date and currency pair
     */
    boolean existsByFromCurrencyAndToCurrencyAndRateDate(
        String fromCurrency, String toCurrency, LocalDateTime rateDate);
    
    /**
     * Delete old historical rates (for cleanup)
     */
    @Modifying
    @Query("DELETE FROM ExchangeRateHistory h WHERE h.rateDate < :cutoffDate")
    void deleteRatesOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Get rate statistics for a currency pair
     */
    @Query("SELECT MIN(h.rate) as minRate, MAX(h.rate) as maxRate, AVG(h.rate) as avgRate " +
           "FROM ExchangeRateHistory h " +
           "WHERE h.fromCurrency = :fromCurrency " +
           "AND h.toCurrency = :toCurrency " +
           "AND h.rateDate BETWEEN :startDate AND :endDate")
    Object[] getRateStatistics(
        @Param("fromCurrency") String fromCurrency,
        @Param("toCurrency") String toCurrency,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}