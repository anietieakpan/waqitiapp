package com.waqiti.investment.dto.request;

import com.waqiti.investment.domain.enums.OptionsStrategyType;
import com.waqiti.investment.domain.enums.OrderSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for options trading strategies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionsTradeRequest {
    
    @NotNull
    private Long accountId;
    
    @NotNull
    private String underlyingSymbol;
    
    @NotNull
    private OptionsStrategyType strategyType;
    
    // Option contract details
    @NotNull
    private LocalDate expirationDate;
    
    @NotNull
    private BigDecimal strikePrice;
    
    @NotNull
    @Positive
    private Integer contracts;
    
    // For multi-leg strategies
    private BigDecimal strike2;
    private LocalDate expiration2;
    private OrderSide side2;
    
    // Strategy parameters
    private BigDecimal maxPremium;
    private BigDecimal minPremium;
    private BigDecimal impliedVolatilityThreshold;
    private BigDecimal deltaThreshold;
    private BigDecimal gammaThreshold;
    private BigDecimal thetaThreshold;
    private BigDecimal vegaThreshold;
    
    // Risk management
    private BigDecimal maxLoss;
    private BigDecimal takeProfitPercent;
    private BigDecimal stopLossPercent;
    
    // Execution preferences
    private Boolean allowEarlyAssignment;
    private Boolean rollOnExpiration;
    private Integer daysToExpirationThreshold;
}