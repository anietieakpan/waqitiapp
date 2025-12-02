/**
 * Crypto Price Response DTO
 * Response containing cryptocurrency price information
 */
package com.waqiti.crypto.dto.response;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoPriceResponse {
    
    private CryptoCurrency currency;
    private BigDecimal currentPrice;
    private BigDecimal priceChange24h;
    private BigDecimal priceChangePercent24h;
    private BigDecimal high24h;
    private BigDecimal low24h;
    private BigDecimal volume24h;
    private BigDecimal marketCap;
    private LocalDateTime lastUpdated;
    private String source;
    private PriceHistory priceHistory;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceHistory {
        private BigDecimal price1h;
        private BigDecimal price24h;
        private BigDecimal price7d;
        private BigDecimal price30d;
        private BigDecimal changePercent1h;
        private BigDecimal changePercent7d;
        private BigDecimal changePercent30d;
    }
}