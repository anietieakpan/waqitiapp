package com.waqiti.investment.domain;

import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderStatus;
import com.waqiti.investment.domain.enums.OrderType;
import com.waqiti.investment.domain.enums.TimeInForce;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"investmentAccount"})
@ToString(exclude = {"investmentAccount"})
@EntityListeners(AuditingEntityListener.class)
public class InvestmentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_account_id", nullable = false)
    private InvestmentAccount investmentAccount;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String instrumentType; // STOCK, ETF, CRYPTO

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeInForce timeInForce;

    // CRITICAL P0 FIX: Add precision and scale to BigDecimal fields for financial accuracy
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal limitPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal stopPrice;

    @Column(precision = 19, scale = 8)
    private BigDecimal executedQuantity = BigDecimal.ZERO;

    @Column(precision = 19, scale = 4)
    private BigDecimal executedPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal averagePrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal orderAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal commission = BigDecimal.ZERO;

    @Column(precision = 19, scale = 4)
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(precision = 19, scale = 4)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    private String brokerageOrderId;

    private String brokerageProvider;

    private String rejectReason;

    private String notes;

    private Boolean isDayTrade = false;

    private String parentOrderId;

    private String linkedOrderId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime submittedAt;

    private LocalDateTime filledAt;

    private LocalDateTime cancelledAt;

    private LocalDateTime expiredAt;

    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    @PreUpdate
    public void calculateTotalCost() {
        if (executedQuantity != null && executedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal subtotal = executedQuantity.multiply(averagePrice != null ? averagePrice : BigDecimal.ZERO);
            this.totalCost = subtotal.add(commission != null ? commission : BigDecimal.ZERO)
                                   .add(fees != null ? fees : BigDecimal.ZERO);
        }
    }

    public void submit() {
        this.status = OrderStatus.PENDING_SUBMIT;
        this.submittedAt = LocalDateTime.now();
    }

    public void accept() {
        this.status = OrderStatus.ACCEPTED;
    }

    public void partialFill(BigDecimal quantity, BigDecimal price) {
        this.executedQuantity = this.executedQuantity.add(quantity);
        if (this.averagePrice == null) {
            this.averagePrice = price;
        } else {
            // Calculate weighted average
            BigDecimal totalValue = this.averagePrice.multiply(this.executedQuantity.subtract(quantity))
                                                     .add(price.multiply(quantity));
            this.averagePrice = totalValue.divide(this.executedQuantity, 4, RoundingMode.HALF_UP);
        }
        this.executedPrice = price;
        this.status = OrderStatus.PARTIALLY_FILLED;
    }

    public void fill(BigDecimal price) {
        this.executedQuantity = this.quantity;
        this.executedPrice = price;
        this.averagePrice = price;
        this.status = OrderStatus.FILLED;
        this.filledAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void reject(String reason) {
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reason;
    }

    public void expire() {
        this.status = OrderStatus.EXPIRED;
        this.expiredAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == OrderStatus.NEW || 
               status == OrderStatus.PENDING_SUBMIT || 
               status == OrderStatus.ACCEPTED ||
               status == OrderStatus.PARTIALLY_FILLED;
    }

    public boolean isFilled() {
        return status == OrderStatus.FILLED;
    }

    public boolean isCancellable() {
        return isActive() && !OrderStatus.PENDING_CANCEL.equals(status);
    }

    public BigDecimal getRemainingQuantity() {
        return quantity.subtract(executedQuantity);
    }

    public boolean isMarketOrder() {
        return OrderType.MARKET.equals(orderType);
    }

    public boolean isLimitOrder() {
        return OrderType.LIMIT.equals(orderType);
    }

    public boolean isStopOrder() {
        return OrderType.STOP.equals(orderType) || OrderType.STOP_LIMIT.equals(orderType);
    }
}