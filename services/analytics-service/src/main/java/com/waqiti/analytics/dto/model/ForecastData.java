package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Forecast data model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastData {
    private LocalDateTime forecastDate;
    private BigDecimal predictedAmount;
    private BigDecimal confidenceInterval;
    private String forecastType;
    private String category;
}