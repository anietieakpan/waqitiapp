package com.waqiti.investment.dto.response;

import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderStatus;
import com.waqiti.investment.domain.enums.OrderType;
import com.waqiti.investment.domain.enums.TimeInForce;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentOrderDto {

    private String id;
    private String investmentAccountId;
    private String orderNumber;
    private String symbol;
    private String instrumentType;
    private String instrumentName;
    private OrderSide side;
    private OrderType orderType;
    private TimeInForce timeInForce;
    private BigDecimal quantity;
    private BigDecimal limitPrice;
    private BigDecimal stopPrice;
    private BigDecimal executedQuantity;
    private BigDecimal executedPrice;
    private BigDecimal averagePrice;
    private BigDecimal orderAmount;
    private BigDecimal commission;
    private BigDecimal fees;
    private BigDecimal totalCost;
    private OrderStatus status;
    private String statusDisplay;
    private String brokerageOrderId;
    private String rejectReason;
    private String notes;
    private Boolean isDayTrade;
    private String parentOrderId;
    private String linkedOrderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime filledAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime expiredAt;
    
    // Calculated fields
    private BigDecimal remainingQuantity;
    private BigDecimal fillPercentage;
    private Boolean isCancellable;
    private Boolean isModifiable;
    private String estimatedValue;
    
    // Market data at order time
    private BigDecimal marketPrice;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    
    public BigDecimal getRemainingQuantity() {
        if (quantity == null || executedQuantity == null) {
            return quantity;
        }
        return quantity.subtract(executedQuantity);
    }
    
    public BigDecimal getFillPercentage() {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (executedQuantity == null) {
            return BigDecimal.ZERO;
        }
        return executedQuantity.divide(quantity, 4, RoundingMode.HALF_UP)
                              .multiply(new BigDecimal("100"));
    }
    
    public boolean isActive() {
        return status != null && status.isActive();
    }
    
    public boolean isComplete() {
        return status != null && status.isTerminal();
    }
    
    public String getOrderTypeDisplay() {
        if (orderType == null) return "";
        
        StringBuilder display = new StringBuilder(orderType.getDisplayName());
        if (limitPrice != null) {
            display.append(" @ $").append(limitPrice);
        }
        if (stopPrice != null) {
            display.append(" Stop: $").append(stopPrice);
        }
        return display.toString();
    }
}