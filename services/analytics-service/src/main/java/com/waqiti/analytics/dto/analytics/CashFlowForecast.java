package com.waqiti.analytics.dto.analytics;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowForecast {
    private LocalDateTime date;
    private BigDecimal predictedNetFlow;
    private BigDecimal confidence;
    private String methodology;
    private BigDecimal upperBound;
    private BigDecimal lowerBound;
}