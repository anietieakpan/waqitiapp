package com.waqiti.investment.broker.dto;

import com.waqiti.investment.broker.enums.ExecutionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Broker Order Response DTO
 * Standardized response from broker after order submission
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
@Data
@Builder
public class BrokerOrderResponse {
    /**
     * Our internal order ID
     */
    private String clientOrderId;

    /**
     * Broker's order ID
     */
    private String brokerOrderId;

    /**
     * Execution status
     */
    private ExecutionStatus status;

    /**
     * Symbol
     */
    private String symbol;

    /**
     * Filled quantity (can be partial)
     */
    private BigDecimal filledQuantity;

    /**
     * Average fill price
     */
    private BigDecimal averageFillPrice;

    /**
     * Total quantity ordered
     */
    private BigDecimal totalQuantity;

    /**
     * Commission charged by broker
     */
    private BigDecimal commission;

    /**
     * Regulatory fees (SEC, FINRA, etc.)
     */
    private BigDecimal fees;

    /**
     * Order creation timestamp at broker
     */
    private LocalDateTime createdAt;

    /**
     * Order fill timestamp
     */
    private LocalDateTime filledAt;

    /**
     * Rejection/failure reason
     */
    private String rejectionReason;

    /**
     * Raw response from broker (for debugging)
     */
    private String rawResponse;
}
