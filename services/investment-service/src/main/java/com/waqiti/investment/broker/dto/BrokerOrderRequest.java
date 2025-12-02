package com.waqiti.investment.broker.dto;

import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderType;
import com.waqiti.investment.domain.enums.TimeInForce;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Broker Order Request DTO
 * Standardized format for submitting orders to any broker
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
@Data
@Builder
public class BrokerOrderRequest {
    /**
     * Our internal order ID
     */
    private String clientOrderId;

    /**
     * Stock symbol (e.g., "AAPL", "TSLA")
     */
    private String symbol;

    /**
     * Buy or sell
     */
    private OrderSide side;

    /**
     * Market, limit, stop, stop-limit
     */
    private OrderType type;

    /**
     * Quantity of shares (can be fractional)
     */
    private BigDecimal quantity;

    /**
     * Limit price (for limit and stop-limit orders)
     */
    private BigDecimal limitPrice;

    /**
     * Stop price (for stop and stop-limit orders)
     */
    private BigDecimal stopPrice;

    /**
     * Time-in-force: DAY, GTC, IOC, FOK
     */
    private TimeInForce timeInForce;

    /**
     * Extended hours trading enabled
     */
    private Boolean extendedHours;

    /**
     * Account identifier at broker
     */
    private String brokerAccountId;
}
