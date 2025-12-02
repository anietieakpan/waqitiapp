package com.waqiti.investment.dto.request;

import com.waqiti.investment.domain.enums.OrderType;
import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.TimeInForce;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request DTO for margin trading
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarginTradeRequest {
    
    @NotNull
    private Long accountId;
    
    @NotNull
    private String symbol;
    
    @NotNull
    private OrderType orderType;
    
    @NotNull
    private OrderSide orderSide;
    
    @NotNull
    @Positive
    private BigDecimal quantity;
    
    private BigDecimal price;
    private BigDecimal stopPrice;
    
    @NotNull
    private TimeInForce timeInForce;
    
    // Margin-specific parameters
    private BigDecimal maxMarginUsage;
    private Boolean useMaxBuyingPower;
    
    // Risk management
    private BigDecimal stopLossPrice;
    private BigDecimal takeProfitPrice;
    private BigDecimal maxDrawdown;
    
    // Interest and fees
    private Boolean acceptInterestCharges;
    private BigDecimal maxDailyInterest;
}