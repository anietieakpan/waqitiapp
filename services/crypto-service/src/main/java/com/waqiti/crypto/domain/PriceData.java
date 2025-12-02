package com.waqiti.crypto.domain;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Data
@Builder
public class PriceData {
    private CryptoCurrency currency;
    private BigDecimal price;
    private LocalDateTime timestamp;
    private String source;
    private BigDecimal change24h;
    private BigDecimal volume24h;
    private BigDecimal marketCap;

    public boolean isExpired(Duration maxAge) {
        return timestamp != null && 
               LocalDateTime.now().isAfter(timestamp.plus(maxAge));
    }

    public long getAgeInSeconds() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).getSeconds();
    }
}