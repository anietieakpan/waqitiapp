package com.waqiti.analytics.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Forecast Data DTO
 *
 * Predicted financial metric for a future date.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastData {

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @NotNull
    private BigDecimal predictedValue;

    private BigDecimal confidence; // 0.0 - 1.0

    private BigDecimal lowerBound;
    private BigDecimal upperBound;

    private String forecastType; // SPENDING, INCOME, BALANCE
}
