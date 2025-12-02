package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trade_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeOrder {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "order_id", unique = true, nullable = false, length = 50)
    private String orderId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "trade_pair_id", nullable = false)
    private UUID tradePairId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_pair_id", insertable = false, updatable = false)
    private TradePair tradePair;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private TradeOrderType orderType;
    
    @Column(name = "side", nullable = false, length = 4)
    private String side; // BUY or SELL
    
    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;
    
    @Column(name = "price", precision = 19, scale = 8)
    private BigDecimal price;
    
    @Column(name = "stop_price", precision = 19, scale = 8)
    private BigDecimal stopPrice;
    
    @Column(name = "total_value", precision = 19, scale = 8)
    private BigDecimal totalValue;
    
    @Column(name = "filled_quantity", precision = 19, scale = 8)
    private BigDecimal filledQuantity = BigDecimal.ZERO;
    
    @Column(name = "remaining_quantity", precision = 19, scale = 8)
    private BigDecimal remainingQuantity;
    
    @Column(name = "average_price", precision = 19, scale = 8)
    private BigDecimal averagePrice;
    
    @Column(name = "trading_fee", precision = 19, scale = 8)
    private BigDecimal tradingFee;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TradeOrderStatus status;
    
    @Column(name = "time_in_force", length = 10)
    private String timeInForce = "GTC"; // Good Till Cancelled
    
    @Column(name = "good_till_time")
    private LocalDateTime goodTillTime;
    
    @Column(name = "client_order_id", length = 100)
    private String clientOrderId;
    
    @Column(name = "parent_order_id")
    private UUID parentOrderId;
    
    @Column(name = "order_book_id", length = 50)
    private String orderBookId;
    
    @Column(name = "priority_score")
    private Long priorityScore;
    
    @Type(type = "jsonb")
    @Column(name = "execution_reports", columnDefinition = "jsonb")
    private Map<String, Object> executionReports;
    
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "reject_reason", length = 255)
    private String rejectReason;
    
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;
    
    @Column(name = "source", length = 50)
    private String source = "WEB"; // WEB, API, MOBILE
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
    
    @Column(name = "first_fill_at")
    private LocalDateTime firstFillAt;
    
    @Column(name = "last_fill_at")
    private LocalDateTime lastFillAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;
    
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (orderId == null) {
            orderId = "ORD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        remainingQuantity = quantity;
        
        // Set priority score based on order type and price
        calculatePriorityScore();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // Update remaining quantity
        if (filledQuantity != null) {
            remainingQuantity = quantity.subtract(filledQuantity);
        }
        
        // Update status based on filled quantity
        updateStatusBasedOnFill();
    }
    
    private void calculatePriorityScore() {
        // Higher priority for market orders
        long baseScore = System.currentTimeMillis();
        
        if (orderType == TradeOrderType.MARKET) {
            priorityScore = baseScore + 1000000; // Higher priority
        } else {
            priorityScore = baseScore;
        }
    }
    
    private void updateStatusBasedOnFill() {
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            status = TradeOrderStatus.FILLED;
            completedAt = LocalDateTime.now();
        } else if (filledQuantity.compareTo(BigDecimal.ZERO) > 0) {
            status = TradeOrderStatus.PARTIALLY_FILLED;
        }
    }
    
    public boolean isActive() {
        return status == TradeOrderStatus.PENDING || 
               status == TradeOrderStatus.PARTIALLY_FILLED ||
               status == TradeOrderStatus.ACKNOWLEDGED;
    }
    
    public boolean isCompleted() {
        return status == TradeOrderStatus.FILLED ||
               status == TradeOrderStatus.CANCELLED ||
               status == TradeOrderStatus.EXPIRED ||
               status == TradeOrderStatus.REJECTED;
    }
    
    public boolean canBeModified() {
        return status == TradeOrderStatus.PENDING ||
               status == TradeOrderStatus.ACKNOWLEDGED;
    }
    
    public BigDecimal getFilledValue() {
        if (filledQuantity == null || averagePrice == null) {
            return BigDecimal.ZERO;
        }
        return filledQuantity.multiply(averagePrice);
    }
    
    public BigDecimal getFillPercentage() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return filledQuantity.divide(quantity, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
}