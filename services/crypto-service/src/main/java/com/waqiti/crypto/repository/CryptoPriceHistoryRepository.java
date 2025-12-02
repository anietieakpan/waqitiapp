/**
 * Crypto Price History Repository
 * JPA repository for cryptocurrency price history operations
 */
package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CryptoPriceHistory;
import com.waqiti.crypto.entity.CryptoCurrency;
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
public interface CryptoPriceHistoryRepository extends JpaRepository<CryptoPriceHistory, UUID> {

    /**
     * Find price history by currency and timestamp after
     */
    List<CryptoPriceHistory> findByCurrencyAndTimestampAfterOrderByTimestampAsc(
        CryptoCurrency currency, LocalDateTime timestamp);

    /**
     * Find price history by currency and timestamp between
     */
    List<CryptoPriceHistory> findByCurrencyAndTimestampBetween(
        CryptoCurrency currency, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find latest price for currency
     */
    Optional<CryptoPriceHistory> findTopByCurrencyOrderByTimestampDesc(CryptoCurrency currency);

    /**
     * Get average price for currency in time period
     */
    @Query("SELECT AVG(p.price) FROM CryptoPriceHistory p WHERE p.currency = :currency " +
           "AND p.timestamp >= :startTime AND p.timestamp <= :endTime")
    BigDecimal getAveragePrice(
        @Param("currency") CryptoCurrency currency,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Get min and max prices for currency in time period
     */
    @Query("SELECT MIN(p.price), MAX(p.price) FROM CryptoPriceHistory p " +
           "WHERE p.currency = :currency AND p.timestamp >= :startTime")
    List<Object[]> getMinMaxPrices(
        @Param("currency") CryptoCurrency currency,
        @Param("startTime") LocalDateTime startTime
    );

    /**
     * Clean up old price history
     */
    void deleteByTimestampBefore(LocalDateTime cutoffTime);
}