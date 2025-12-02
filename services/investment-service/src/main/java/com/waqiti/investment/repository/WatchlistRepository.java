package com.waqiti.investment.repository;

import com.waqiti.investment.domain.WatchlistItem;
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

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistItem, String> {

    Optional<WatchlistItem> findByCustomerIdAndSymbol(String customerId, String symbol);

    List<WatchlistItem> findByCustomerId(String customerId);

    Page<WatchlistItem> findByCustomerId(String customerId, Pageable pageable);

    List<WatchlistItem> findBySymbol(String symbol);

    boolean existsByCustomerIdAndSymbol(String customerId, String symbol);

    void deleteByCustomerIdAndSymbol(String customerId, String symbol);

    @Query("SELECT w FROM WatchlistItem w WHERE w.customerId = :customerId " +
           "AND w.alertsEnabled = true")
    List<WatchlistItem> findActiveAlerts(@Param("customerId") String customerId);

    @Query("SELECT w FROM WatchlistItem w WHERE w.alertsEnabled = true " +
           "AND (w.currentPrice >= w.priceAlertAbove OR w.currentPrice <= w.priceAlertBelow)")
    List<WatchlistItem> findTriggeredPriceAlerts();

    @Query("SELECT w FROM WatchlistItem w WHERE w.dayChangePercent > :threshold " +
           "ORDER BY w.dayChangePercent DESC")
    List<WatchlistItem> findTopGainers(@Param("threshold") BigDecimal threshold);

    @Query("SELECT w FROM WatchlistItem w WHERE w.dayChangePercent < :threshold " +
           "ORDER BY w.dayChangePercent ASC")
    List<WatchlistItem> findTopLosers(@Param("threshold") BigDecimal threshold);

    @Query("SELECT w FROM WatchlistItem w WHERE w.lastPriceUpdate < :threshold")
    List<WatchlistItem> findStaleItems(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT w.symbol, COUNT(w) FROM WatchlistItem w " +
           "GROUP BY w.symbol ORDER BY COUNT(w) DESC")
    List<Object[]> getMostWatchedSymbols();

    @Query("SELECT w FROM WatchlistItem w WHERE w.customerId = :customerId " +
           "AND w.instrumentType = :type")
    List<WatchlistItem> findByCustomerIdAndInstrumentType(@Param("customerId") String customerId,
                                                          @Param("type") String type);

    @Query("SELECT w FROM WatchlistItem w WHERE w.currentPrice >= w.fiftyTwoWeekHigh * 0.95")
    List<WatchlistItem> findNearFiftyTwoWeekHighs();

    @Query("SELECT w FROM WatchlistItem w WHERE w.currentPrice <= w.fiftyTwoWeekLow * 1.05")
    List<WatchlistItem> findNearFiftyTwoWeekLows();

    @Query("SELECT w FROM WatchlistItem w WHERE w.volume > w.averageVolume * :multiplier")
    List<WatchlistItem> findHighVolumeItems(@Param("multiplier") BigDecimal multiplier);

    @Query("SELECT w FROM WatchlistItem w WHERE w.targetPrice IS NOT NULL " +
           "AND w.currentPrice >= w.targetPrice")
    List<WatchlistItem> findItemsAtTargetPrice();

    @Query("SELECT COUNT(w) FROM WatchlistItem w WHERE w.customerId = :customerId")
    long countByCustomerId(@Param("customerId") String customerId);

    @Query("SELECT w.sector, COUNT(w) FROM WatchlistItem w WHERE w.sector IS NOT NULL " +
           "GROUP BY w.sector ORDER BY COUNT(w) DESC")
    List<Object[]> getWatchlistBySector();

    @Query("SELECT AVG(w.dayChangePercent) FROM WatchlistItem w WHERE w.customerId = :customerId")
    BigDecimal getAverageDayChange(@Param("customerId") String customerId);

    @Query("SELECT w FROM WatchlistItem w WHERE w.peRatio < :maxPE AND w.peRatio > 0 " +
           "ORDER BY w.peRatio ASC")
    List<WatchlistItem> findValueStocks(@Param("maxPE") BigDecimal maxPE);

    @Query("SELECT w FROM WatchlistItem w WHERE w.dividendYield > :minYield " +
           "ORDER BY w.dividendYield DESC")
    List<WatchlistItem> findHighDividendStocks(@Param("minYield") BigDecimal minYield);
}