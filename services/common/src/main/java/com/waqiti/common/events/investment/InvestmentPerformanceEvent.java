package com.waqiti.common.events.investment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentPerformanceEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "Investment ID is required")
    @JsonProperty("investment_id")
    private String investmentId;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Investment type is required")
    @JsonProperty("investment_type")
    private String investmentType;

    @NotBlank(message = "Asset class is required")
    @JsonProperty("asset_class")
    private String assetClass;

    @NotNull(message = "Initial investment is required")
    @JsonProperty("initial_investment")
    private BigDecimal initialInvestment;

    @NotNull(message = "Current value is required")
    @JsonProperty("current_value")
    private BigDecimal currentValue;

    @JsonProperty("total_gain_loss")
    private BigDecimal totalGainLoss;

    @JsonProperty("total_gain_loss_percentage")
    private BigDecimal totalGainLossPercentage;

    @JsonProperty("daily_return")
    private BigDecimal dailyReturn;

    @JsonProperty("daily_return_percentage")
    private BigDecimal dailyReturnPercentage;

    @JsonProperty("return_rate")
    private BigDecimal returnRate;

    @JsonProperty("return_period")
    private String returnPeriod;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @NotNull(message = "Performance date is required")
    @JsonProperty("performance_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate performanceDate;

    @JsonProperty("year_to_date_return")
    private BigDecimal yearToDateReturn;

    @JsonProperty("all_time_return")
    private BigDecimal allTimeReturn;

    @JsonProperty("portfolio_allocation_percentage")
    private BigDecimal portfolioAllocationPercentage;
}