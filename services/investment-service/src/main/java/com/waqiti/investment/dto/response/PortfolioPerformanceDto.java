package com.waqiti.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for portfolio performance metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioPerformanceDto {
    private Long accountId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal initialValue;
    private BigDecimal currentValue;
    private BigDecimal totalReturn;
    private BigDecimal returnPercentage;
    private BigDecimal annualizedReturn;
    private BigDecimal volatility;
    private BigDecimal sharpeRatio;
    private BigDecimal beta;
    private BigDecimal alpha;
    private BigDecimal maxDrawdown;
    private LocalDate maxDrawdownDate;
}