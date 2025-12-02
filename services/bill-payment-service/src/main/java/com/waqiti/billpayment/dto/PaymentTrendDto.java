package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for payment trend analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentTrendDto {

    private String trendDirection; // INCREASING, DECREASING, STABLE

    private BigDecimal percentageChange;

    private List<MonthlySpendingDto> monthlyData;

    private BigDecimal projectedNextMonth;

    private String currency;
}
