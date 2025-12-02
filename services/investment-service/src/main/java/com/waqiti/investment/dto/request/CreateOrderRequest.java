package com.waqiti.investment.dto.request;

import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderType;
import com.waqiti.investment.domain.enums.TimeInForce;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Order side is required")
    private OrderSide side;

    @NotNull(message = "Order type is required")
    private OrderType orderType;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than 0")
    private BigDecimal quantity;

    private BigDecimal limitPrice;

    private BigDecimal stopPrice;

    @NotNull(message = "Time in force is required")
    private TimeInForce timeInForce;

    private Boolean extendedHours;

    private Boolean allOrNone;

    private Boolean doNotReduce;

    private String notes;

    private String parentOrderId;

    private String linkedOrderId;

    private Boolean fractionalShares;

    private String metadata;

    // Validation methods
    public void validate() {
        if (orderType.requiresPrice() && limitPrice == null) {
            throw new IllegalArgumentException("Limit price is required for " + orderType);
        }
        
        if (orderType.requiresStopPrice() && stopPrice == null) {
            throw new IllegalArgumentException("Stop price is required for " + orderType);
        }
        
        if (OrderType.STOP_LIMIT.equals(orderType) && limitPrice != null && stopPrice != null) {
            if (OrderSide.BUY.equals(side) && limitPrice.compareTo(stopPrice) > 0) {
                throw new IllegalArgumentException("For buy stop-limit orders, limit price must be less than or equal to stop price");
            }
            if (OrderSide.SELL.equals(side) && limitPrice.compareTo(stopPrice) < 0) {
                throw new IllegalArgumentException("For sell stop-limit orders, limit price must be greater than or equal to stop price");
            }
        }
    }
}