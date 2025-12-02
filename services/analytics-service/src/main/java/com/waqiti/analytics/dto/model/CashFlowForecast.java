package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cash flow forecast model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowForecast {
    private LocalDateTime forecastDate;
    private BigDecimal predictedInflow;
    private BigDecimal predictedOutflow;
    private BigDecimal predictedNetFlow;
    private BigDecimal confidence;
}