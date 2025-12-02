package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for cryptocurrency price
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoPriceResponse {
    private String currency;
    private String fiatCurrency;
    private BigDecimal price;
    private BigDecimal priceChange24h;
    private BigDecimal priceChangePercentage24h;
    private BigDecimal high24h;
    private BigDecimal low24h;
    private BigDecimal volume24h;
    private LocalDateTime timestamp;
    private String source; // Exchange/oracle source
    private boolean available;
    private String errorMessage;

    public static CryptoPriceResponse unavailable(String message) {
        return CryptoPriceResponse.builder()
                .available(false)
                .errorMessage(message)
                .price(BigDecimal.ZERO)
                .build();
    }
}
