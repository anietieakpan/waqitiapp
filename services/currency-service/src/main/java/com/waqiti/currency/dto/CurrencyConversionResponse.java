package com.waqiti.currency.dto;

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
public class CurrencyConversionResponse {
    
    private BigDecimal originalAmount;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal convertedAmount;
    private BigDecimal exchangeRate;
    private LocalDateTime rateTimestamp;
    private String rateSource;
    private BigDecimal margin;
    private BigDecimal totalFees;
    private String conversionId;
    private LocalDateTime conversionTimestamp;
}