package com.waqiti.investment.domain;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_holdings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"investmentAccount", "portfolio"})
@ToString(exclude = {"investmentAccount", "portfolio"})
@EntityListeners(AuditingEntityListener.class)
public class InvestmentHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_account_id", nullable = false)
    private InvestmentAccount investmentAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String instrumentType; // STOCK, ETF, CRYPTO

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal currentPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal marketValue = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal dayChange = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal dayChangePercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalReturn = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalReturnPercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal realizedGains = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal unrealizedGains = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal dividendEarnings = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal portfolioPercentage = BigDecimal.ZERO;

    private BigDecimal previousClose;

    private BigDecimal dayLow;

    private BigDecimal dayHigh;

    private BigDecimal fiftyTwoWeekLow;

    private BigDecimal fiftyTwoWeekHigh;

    private Long volume;

    private Long averageVolume;

    private BigDecimal marketCap;

    private BigDecimal peRatio;

    private BigDecimal dividendYield;

    private BigDecimal beta;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime firstPurchaseDate;

    private LocalDateTime lastPurchaseDate;

    private LocalDateTime lastPriceUpdate;

    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public void updateQuantity(BigDecimal quantity, BigDecimal cost) {
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            // Adding shares
            BigDecimal newTotalCost = this.totalCost.add(cost);
            this.quantity = this.quantity.add(quantity);
            this.totalCost = newTotalCost;
            this.averageCost = newTotalCost.divide(this.quantity, 4, RoundingMode.HALF_UP);
            this.lastPurchaseDate = LocalDateTime.now();
            if (this.firstPurchaseDate == null) {
                this.firstPurchaseDate = LocalDateTime.now();
            }
        } else {
            // Selling shares
            BigDecimal absQuantity = quantity.abs();
            if (absQuantity.compareTo(this.quantity) >= 0) {
                // Selling all shares
                BigDecimal realizedGain = this.currentPrice.multiply(this.quantity)
                                                          .subtract(this.totalCost);
                this.realizedGains = this.realizedGains.add(realizedGain);
                this.quantity = BigDecimal.ZERO;
                this.totalCost = BigDecimal.ZERO;
                this.averageCost = BigDecimal.ZERO;
            } else {
                // Partial sale
                BigDecimal soldCost = this.averageCost.multiply(absQuantity);
                BigDecimal saleProceeds = this.currentPrice.multiply(absQuantity);
                BigDecimal realizedGain = saleProceeds.subtract(soldCost);

                this.realizedGains = this.realizedGains.add(realizedGain);
                this.quantity = this.quantity.subtract(absQuantity);
                this.totalCost = this.totalCost.subtract(soldCost);
            }
        }
        updateMarketValue();
        updateReturns();
    }

    public void updatePrice(BigDecimal currentPrice, BigDecimal dayChange, BigDecimal dayChangePercent) {
        this.currentPrice = currentPrice;
        this.dayChange = dayChange;
        this.dayChangePercent = dayChangePercent;
        this.lastPriceUpdate = LocalDateTime.now();
        updateMarketValue();
        updateReturns();
    }

    private void updateMarketValue() {
        this.marketValue = this.currentPrice.multiply(this.quantity);
    }

    private void updateReturns() {
        if (this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            this.unrealizedGains = this.marketValue.subtract(this.totalCost);
            this.totalReturn = this.unrealizedGains.add(this.realizedGains).add(this.dividendEarnings);
            if (this.totalCost.compareTo(BigDecimal.ZERO) > 0) {
                this.totalReturnPercent = this.totalReturn.divide(this.totalCost, 4, RoundingMode.HALF_UP)
                                                         .multiply(new BigDecimal("100"));
            }
        }
    }

    public void updateMarketData(BigDecimal previousClose, BigDecimal dayLow, BigDecimal dayHigh,
                                BigDecimal fiftyTwoWeekLow, BigDecimal fiftyTwoWeekHigh,
                                Long volume, Long averageVolume, BigDecimal marketCap,
                                BigDecimal peRatio, BigDecimal dividendYield, BigDecimal beta) {
        this.previousClose = previousClose;
        this.dayLow = dayLow;
        this.dayHigh = dayHigh;
        this.fiftyTwoWeekLow = fiftyTwoWeekLow;
        this.fiftyTwoWeekHigh = fiftyTwoWeekHigh;
        this.volume = volume;
        this.averageVolume = averageVolume;
        this.marketCap = marketCap;
        this.peRatio = peRatio;
        this.dividendYield = dividendYield;
        this.beta = beta;
    }

    public void addDividend(BigDecimal amount) {
        this.dividendEarnings = this.dividendEarnings.add(amount);
        updateReturns();
    }

    public void updatePortfolioPercentage(BigDecimal percentage) {
        this.portfolioPercentage = percentage;
    }

    public boolean hasPosition() {
        return this.quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isProfitable() {
        return this.unrealizedGains.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getGainLossPercent() {
        if (this.totalCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return this.unrealizedGains.divide(this.totalCost, 4, RoundingMode.HALF_UP)
                                  .multiply(new BigDecimal("100"));
    }
}