package com.waqiti.investment.execution.model;

import com.waqiti.investment.domain.InvestmentOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order Book Entry - represents a single order on the order book.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookEntry {

    private InvestmentOrder order;
    private BigDecimal price;
    private BigDecimal quantity;
    private LocalDateTime timestamp;

    /**
     * Get order ID for quick reference.
     */
    public String getOrderId() {
        return order != null ? order.getId() : null;
    }
}
