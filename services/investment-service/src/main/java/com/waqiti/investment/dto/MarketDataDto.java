package com.waqiti.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for market data information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataDto {
    private String symbol;
    private String name;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private Long volume;
    private LocalDateTime lastUpdated;
}