package com.waqiti.investment.dto.request;

import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.AlgorithmicStrategyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request DTO for algorithmic trading strategies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmicTradeRequest {
    
    @NotNull
    private Long accountId;
    
    @NotNull
    private String symbol;
    
    @NotNull
    private AlgorithmicStrategyType strategyType;
    
    @NotNull
    private OrderSide orderSide;
    
    @NotNull
    @Positive
    private BigDecimal quantity;
    
    // TWAP/VWAP parameters
    private Integer timeSlices;
    private Integer timeIntervalMinutes;
    
    // Iceberg parameters
    private BigDecimal icebergVisibleQuantity;
    
    // Limit order parameters
    private BigDecimal limitPrice;
    
    // Risk management
    private BigDecimal maxSlippage;
    private BigDecimal stopLossPrice;
    private BigDecimal takeProfitPrice;
    
    // Strategy-specific parameters
    private BigDecimal rsiThreshold;
    private BigDecimal volumeThreshold;
    private BigDecimal priceChangeThreshold;
    
    // Execution parameters
    private Integer maxExecutionTimeMinutes;
    private Boolean allowPartialFill;
    private BigDecimal minFillQuantity;
}