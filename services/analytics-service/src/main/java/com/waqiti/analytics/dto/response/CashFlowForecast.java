package com.waqiti.analytics.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cash Flow Forecast DTO
 *
 * Predicted cash flow for a future date based on historical patterns.
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowForecast {

    @NotNull(message = "Forecast date cannot be null")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate forecastDate;

    @NotNull(message = "Predicted amount cannot be null")
    private BigDecimal predictedAmount;

    @PositiveOrZero(message = "Confidence level must be non-negative")
    private BigDecimal confidenceLevel; // 0.0 - 1.0

    private BigDecimal lowerBound;
    private BigDecimal upperBound;
}
