package com.waqiti.investment.dto.request;

import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request DTO for creating bracket orders (OCO - One Cancels Other)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBracketOrderRequest {
    
    @NotNull
    private Long accountId;
    
    @NotNull
    private String symbol;
    
    @NotNull
    private OrderType parentOrderType;
    
    @NotNull
    private OrderSide orderSide;
    
    @NotNull
    @Positive
    private BigDecimal quantity;
    
    private BigDecimal parentLimitPrice;
    
    private BigDecimal profitTargetPrice;
    
    private BigDecimal stopLossPrice;
    
    // Risk management parameters
    private BigDecimal maxRiskPercent;
    private BigDecimal minProfitPercent;
    
    // Timing parameters
    private Integer timeoutMinutes;
    private Boolean trailingStop;
    private BigDecimal trailingStopPercent;
}