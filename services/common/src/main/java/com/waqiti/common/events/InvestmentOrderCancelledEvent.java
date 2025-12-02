package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Event triggered when an investment order is cancelled
 * Critical for portfolio management, risk assessment, and investor notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentOrderCancelledEvent implements DomainEvent {
    
    private String eventId;
    private Instant timestamp;
    private String orderId;
    private String userId;
    private String accountId;
    private String symbol;
    private BigDecimal quantity;
    private String orderType; // BUY, SELL, SELL_SHORT
    private String cancellationReason;
    private String cancelledBy; // USER, SYSTEM, ADMIN, COMPLIANCE
    private BigDecimal orderAmount;
    private BigDecimal limitPrice;
    private BigDecimal stopPrice;
    private String timeInForce; // DAY, GTC, IOC, FOK
    private String orderStatus; // PENDING, PARTIALLY_FILLED
    private BigDecimal filledQuantity; // If partially filled
    private BigDecimal executedAmount; // If partially filled
    private boolean wasPartiallyFilled;
    
    // Cancellation context
    private String cancellationSource; // WEB, MOBILE, API, AUTOMATED_RISK_CHECK
    private boolean wasRejectedBySystem;
    private String rejectionReason;
    
    // Financial impact
    private BigDecimal refundAmount; // If fees were charged
    private BigDecimal heldAmount; // Amount that was being held for order
    
    // Compliance flags
    private boolean complianceRelated;
    private String complianceFlag;
    
    @Override
    public String getEventType() {
        return "InvestmentOrderCancelledEvent";
    }
    
    @Override
    public String getAggregateId() {
        return orderId;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return java.util.Collections.emptyMap();
    }

    @Override
    public String getTopic() {
        return "investment-orders";
    }

    @Override
    public String getAggregateType() {
        return "InvestmentOrder";
    }

    @Override
    public String getAggregateName() {
        return "Investment Order";
    }

    @Override
    public Long getVersion() {
        return 1L;
    }

    @Override
    public String getCorrelationId() {
        return eventId;
    }

    @Override
    public String getSourceService() {
        return "investment-service";
    }
}