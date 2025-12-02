package com.waqiti.investment.execution.model;

import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Execution Report - trade execution confirmation.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionReport {

    private String executionId;
    private String orderId;
    private String symbol;
    private OrderSide side;
    private BigDecimal executedQuantity;
    private BigDecimal executionPrice;
    private LocalDateTime executionTime;
    private String aggressorOrderId;
    private String restingOrderId;
    private BigDecimal remainingQuantity;
    private OrderStatus orderStatus;
    private String exchange;
    private BigDecimal commission;
    private BigDecimal fees;
}
