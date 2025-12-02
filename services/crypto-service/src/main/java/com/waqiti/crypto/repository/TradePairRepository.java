package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.TradePair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TradePair with optimized fetch strategies
 * Prevents N+1 queries when loading currency information
 */
@Repository
public interface TradePairRepository extends JpaRepository<TradePair, UUID> {
    
    /**
     * Find trade pair by symbol - basic info only (no currency fetching)
     */
    Optional<TradePair> findBySymbol(String symbol);
    
    /**
     * Find trade pair with currency details - explicit JOIN FETCH for trading engine
     */
    @Query("SELECT tp FROM TradePair tp " +
           "JOIN FETCH tp.tradeCurrency tc " +
           "JOIN FETCH tp.baseCurrency bc " +
           "WHERE tp.symbol = :symbol")
    Optional<TradePair> findBySymbolWithCurrencies(@Param("symbol") String symbol);
    
    /**
     * Find all active trade pairs with currency details - market data display
     */
    @Query("SELECT tp FROM TradePair tp " +
           "JOIN FETCH tp.tradeCurrency tc " +
           "JOIN FETCH tp.baseCurrency bc " +
           "WHERE tp.active = true ORDER BY tp.priority DESC, tp.symbol ASC")
    List<TradePair> findAllActiveWithCurrencies();
    
    /**
     * Find active trading-enabled pairs with currencies - order book
     */
    @Query("SELECT tp FROM TradePair tp " +
           "JOIN FETCH tp.tradeCurrency tc " +
           "JOIN FETCH tp.baseCurrency bc " +
           "WHERE tp.active = true AND tp.tradingEnabled = true " +
           "ORDER BY tp.priority DESC")
    List<TradePair> findTradingEnabledWithCurrencies();
    
    /**
     * Find pairs by trade currency - portfolio management
     */
    @Query("SELECT tp FROM TradePair tp WHERE tp.tradeCurrency.id = :currencyId AND tp.active = true")
    List<TradePair> findByTradeCurrencyId(@Param("currencyId") UUID currencyId);
    
    /**
     * Find pairs by base currency - conversion calculations
     */
    @Query("SELECT tp FROM TradePair tp WHERE tp.baseCurrency.id = :currencyId AND tp.active = true")
    List<TradePair> findByBaseCurrencyId(@Param("currencyId") UUID currencyId);
    
    /**
     * Find pairs involving specific currency (either trade or base) with currency details
     */
    @Query("SELECT tp FROM TradePair tp " +
           "JOIN FETCH tp.tradeCurrency tc " +
           "JOIN FETCH tp.baseCurrency bc " +
           "WHERE (tp.tradeCurrency.id = :currencyId OR tp.baseCurrency.id = :currencyId) " +
           "AND tp.active = true")
    List<TradePair> findPairsInvolvingCurrencyWithDetails(@Param("currencyId") UUID currencyId);
    
    /**
     * Find high-volume pairs - featured trading pairs
     */
    @Query("SELECT tp FROM TradePair tp " +
           "JOIN FETCH tp.tradeCurrency tc " +
           "JOIN FETCH tp.baseCurrency bc " +
           "WHERE tp.active = true AND tp.tradingEnabled = true " +
           "AND tp.volume24h >= :minVolume " +
           "ORDER BY tp.volume24h DESC")
    List<TradePair> findHighVolumePairsWithCurrencies(@Param("minVolume") BigDecimal minVolume);
    
    /**
     * Find pairs with price alerts - notification system
     */
    @Query("SELECT tp FROM TradePair tp WHERE tp.active = true AND tp.hasPriceAlerts = true")
    List<TradePair> findPairsWithPriceAlerts();
    
    /**
     * Get paginated pairs with currency info for admin dashboard
     */
    @Query("SELECT tp FROM TradePair tp " +
           "JOIN FETCH tp.tradeCurrency tc " +
           "JOIN FETCH tp.baseCurrency bc " +
           "WHERE (:active IS NULL OR tp.active = :active) " +
           "ORDER BY tp.createdAt DESC")
    Page<TradePair> findAllWithCurrencies(@Param("active") Boolean active, Pageable pageable);
    
    /**
     * Update trading status efficiently - market maintenance
     */
    @Modifying
    @Query("UPDATE TradePair tp SET tp.tradingEnabled = :enabled, tp.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE tp.id IN :pairIds")
    int updateTradingStatus(@Param("pairIds") List<UUID> pairIds, @Param("enabled") Boolean enabled);
    
    /**
     * Update current price efficiently - price feed
     */
    @Modifying
    @Query("UPDATE TradePair tp SET tp.currentPrice = :price, tp.priceChange24h = :change24h, " +
           "tp.volume24h = :volume24h, tp.lastPriceUpdate = CURRENT_TIMESTAMP, " +
           "tp.updatedAt = CURRENT_TIMESTAMP WHERE tp.symbol = :symbol")
    int updatePriceData(@Param("symbol") String symbol, 
                        @Param("price") BigDecimal price,
                        @Param("change24h") BigDecimal change24h,
                        @Param("volume24h") BigDecimal volume24h);
    
    /**
     * Find pairs needing price updates - stale data detection
     */
    @Query("SELECT tp FROM TradePair tp WHERE tp.active = true AND tp.tradingEnabled = true " +
           "AND (tp.lastPriceUpdate IS NULL OR tp.lastPriceUpdate < :cutoffTime)")
    List<TradePair> findPairsNeedingPriceUpdate(@Param("cutoffTime") java.time.LocalDateTime cutoffTime);
    
    /**
     * Get market summary data - dashboard statistics
     */
    @Query("SELECT COUNT(tp), SUM(tp.volume24h), AVG(tp.currentPrice) " +
           "FROM TradePair tp WHERE tp.active = true AND tp.tradingEnabled = true")
    Object[] getMarketSummary();
    
    /**
     * Find pairs by fee range - cost analysis
     */
    @Query("SELECT tp FROM TradePair tp WHERE tp.active = true " +
           "AND tp.makerFee BETWEEN :minFee AND :maxFee " +
           "ORDER BY tp.makerFee ASC")
    List<TradePair> findByFeeRange(@Param("minFee") BigDecimal minFee, @Param("maxFee") BigDecimal maxFee);
    
    /**
     * Check if trading is enabled for symbol - order validation
     */
    @Query("SELECT tp.tradingEnabled FROM TradePair tp WHERE tp.symbol = :symbol AND tp.active = true")
    Optional<Boolean> isTradingEnabled(@Param("symbol") String symbol);
    
    /**
     * Get trade pair limits - order validation
     */
    @Query("SELECT tp.minTradeAmount, tp.maxTradeAmount, tp.minPrice, tp.maxPrice, tp.tickSize, tp.lotSize " +
           "FROM TradePair tp WHERE tp.symbol = :symbol AND tp.active = true")
    Optional<Object[]> getTradeLimits(@Param("symbol") String symbol);
    
    /**
     * Find similar pairs for recommendations - trading suggestions
     */
    @Query("SELECT tp FROM TradePair tp " +
           "JOIN FETCH tp.tradeCurrency tc " +
           "JOIN FETCH tp.baseCurrency bc " +
           "WHERE tp.tradeCurrency.id = :tradeCurrencyId " +
           "AND tp.baseCurrency.id != :excludeBaseCurrencyId " +
           "AND tp.active = true AND tp.tradingEnabled = true " +
           "ORDER BY tp.volume24h DESC")
    List<TradePair> findSimilarPairs(@Param("tradeCurrencyId") UUID tradeCurrencyId,
                                     @Param("excludeBaseCurrencyId") UUID excludeBaseCurrencyId,
                                     Pageable pageable);
    
    /**
     * Update priority for featured pairs - promotional management
     */
    @Modifying
    @Query("UPDATE TradePair tp SET tp.priority = :priority, tp.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE tp.symbol = :symbol")
    int updatePriority(@Param("symbol") String symbol, @Param("priority") Integer priority);
    
    /**
     * Find pairs with recent high volatility - risk management
     */
    @Query("SELECT tp FROM TradePair tp WHERE tp.active = true " +
           "AND ABS(tp.priceChange24h) >= :volatilityThreshold " +
           "ORDER BY ABS(tp.priceChange24h) DESC")
    List<TradePair> findHighVolatilityPairs(@Param("volatilityThreshold") BigDecimal volatilityThreshold);
    
    /**
     * Bulk update active status - market maintenance
     */
    @Modifying
    @Query("UPDATE TradePair tp SET tp.active = :active, tp.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE tp.symbol IN :symbols")
    int updateActiveStatus(@Param("symbols") List<String> symbols, @Param("active") Boolean active);
}