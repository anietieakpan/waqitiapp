package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketRisk {
    private BigDecimal interestRateRisk;
    private BigDecimal equityRisk;
    private BigDecimal foreignExchangeRisk;
    private BigDecimal commodityRisk;
    private BigDecimal totalMarketRisk;
}
