package com.waqiti.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for stock quote information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuoteDto {
    private String symbol;
    private String name;
    private BigDecimal price;
    private BigDecimal previousClose;
    private BigDecimal open;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private Long volume;
    private Long avgVolume;
    private BigDecimal marketCap;
    private BigDecimal peRatio;
    private BigDecimal eps;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal yearHigh;
    private BigDecimal yearLow;
    private LocalDateTime lastTradeTime;
    
    // Additional fields for extended quotes
    private BigDecimal bid;
    private BigDecimal ask;
    private Long bidSize;
    private Long askSize;
    private BigDecimal dividendYield;
    private BigDecimal dayGainLoss;
    private BigDecimal dayGainLossPercentage;
}