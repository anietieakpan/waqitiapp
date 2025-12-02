package com.waqiti.currency.repository;

import com.waqiti.currency.domain.ExchangeRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    
    Optional<ExchangeRate> findByBaseCurrencyAndTargetCurrencyAndEffectiveDate(
            String baseCurrency, String targetCurrency, LocalDateTime effectiveDate);
    
    @Query("SELECT e FROM ExchangeRate e WHERE e.baseCurrency = :baseCurrency " +
           "AND e.targetCurrency = :targetCurrency " +
           "AND e.effectiveDate <= :date " +
           "ORDER BY e.effectiveDate DESC")
    Optional<ExchangeRate> findLatestRateBeforeDate(
            @Param("baseCurrency") String baseCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("date") LocalDateTime date);
    
    @Query("SELECT e FROM ExchangeRate e WHERE e.baseCurrency = :baseCurrency " +
           "AND e.targetCurrency = :targetCurrency " +
           "AND e.effectiveDate BETWEEN :startDate AND :endDate " +
           "ORDER BY e.effectiveDate ASC")
    Page<ExchangeRate> findHistoricalRates(
            @Param("baseCurrency") String baseCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    @Query("SELECT DISTINCT e.targetCurrency FROM ExchangeRate e WHERE e.baseCurrency = :baseCurrency")
    List<String> findAvailableTargetCurrencies(@Param("baseCurrency") String baseCurrency);
    
    @Query("SELECT e FROM ExchangeRate e WHERE e.baseCurrency = :baseCurrency " +
           "AND e.effectiveDate = (SELECT MAX(er.effectiveDate) FROM ExchangeRate er " +
           "WHERE er.baseCurrency = :baseCurrency AND er.targetCurrency = e.targetCurrency)")
    List<ExchangeRate> findLatestRatesForBaseCurrency(@Param("baseCurrency") String baseCurrency);
    
    @Query("SELECT AVG(e.rate) FROM ExchangeRate e WHERE e.baseCurrency = :baseCurrency " +
           "AND e.targetCurrency = :targetCurrency " +
           "AND e.effectiveDate BETWEEN :startDate AND :endDate")
    BigDecimal getAverageRate(
            @Param("baseCurrency") String baseCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT MAX(e.rate) FROM ExchangeRate e WHERE e.baseCurrency = :baseCurrency " +
           "AND e.targetCurrency = :targetCurrency " +
           "AND e.effectiveDate BETWEEN :startDate AND :endDate")
    BigDecimal getHighestRate(
            @Param("baseCurrency") String baseCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT MIN(e.rate) FROM ExchangeRate e WHERE e.baseCurrency = :baseCurrency " +
           "AND e.targetCurrency = :targetCurrency " +
           "AND e.effectiveDate BETWEEN :startDate AND :endDate")
    BigDecimal getLowestRate(
            @Param("baseCurrency") String baseCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT e FROM ExchangeRate e WHERE e.effectiveDate >= :date " +
           "AND e.baseCurrency IN :currencies")
    List<ExchangeRate> findRatesFromDate(
            @Param("date") LocalDateTime date,
            @Param("currencies") List<String> currencies);
    
    boolean existsByBaseCurrencyAndTargetCurrencyAndEffectiveDate(
            String baseCurrency, String targetCurrency, LocalDateTime effectiveDate);
    
    void deleteByEffectiveDateBefore(LocalDateTime date);
}