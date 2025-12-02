/**
 * Balance Response DTO
 * Response containing balance information
 */
package com.waqiti.crypto.dto.response;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {
    
    private UUID userId;
    private List<CurrencyBalance> balances;
    private BigDecimal totalUsdValue;
    private LocalDateTime lastUpdated;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyBalance {
        private CryptoCurrency currency;
        private BigDecimal available;
        private BigDecimal pending;
        private BigDecimal locked;
        private BigDecimal total;
        private BigDecimal usdValue;
        private BigDecimal currentPrice;
        private BigDecimal change24h;
        private BigDecimal changePercent24h;
        private Integer walletCount;
    }
}