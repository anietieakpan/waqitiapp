package com.waqiti.investment.domain;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"investmentAccount", "holdings"})
@ToString(exclude = {"investmentAccount", "holdings"})
@EntityListeners(AuditingEntityListener.class)
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents concurrent portfolio value updates
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    
    /**
     * Audit fields for investment compliance
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;

    @OneToOne
    @JoinColumn(name = "investment_account_id", nullable = false)
    private InvestmentAccount investmentAccount;

    @Column(nullable = false)
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalReturn = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalReturnPercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal dayChange = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal dayChangePercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal realizedGains = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal unrealizedGains = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal dividendEarnings = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer numberOfPositions = 0;

    @Column(nullable = false)
    private BigDecimal cashPercentage = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal equityPercentage = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal etfPercentage = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal cryptoPercentage = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal diversificationScore = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal riskScore = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal volatility = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal sharpeRatio = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal beta = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal alpha = BigDecimal.ZERO;

    private String topPerformer;

    private String worstPerformer;

    private BigDecimal topPerformerReturn;

    private BigDecimal worstPerformerReturn;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvestmentHolding> holdings = new ArrayList<>();

    private LocalDateTime lastRebalancedAt;

    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public void updatePortfolioMetrics(BigDecimal totalValue, BigDecimal dayChange, BigDecimal dayChangePercent) {
        this.totalValue = totalValue;
        this.dayChange = dayChange;
        this.dayChangePercent = dayChangePercent;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateReturns(BigDecimal totalReturn, BigDecimal totalReturnPercent) {
        this.totalReturn = totalReturn;
        this.totalReturnPercent = totalReturnPercent;
    }

    public void updateGains(BigDecimal realizedGains, BigDecimal unrealizedGains) {
        this.realizedGains = realizedGains;
        this.unrealizedGains = unrealizedGains;
    }

    public void updateAssetAllocation(BigDecimal cashPercentage, BigDecimal equityPercentage, 
                                     BigDecimal etfPercentage, BigDecimal cryptoPercentage) {
        this.cashPercentage = cashPercentage;
        this.equityPercentage = equityPercentage;
        this.etfPercentage = etfPercentage;
        this.cryptoPercentage = cryptoPercentage;
    }

    public void updateRiskMetrics(BigDecimal diversificationScore, BigDecimal riskScore, 
                                  BigDecimal volatility, BigDecimal sharpeRatio, 
                                  BigDecimal beta, BigDecimal alpha) {
        this.diversificationScore = diversificationScore;
        this.riskScore = riskScore;
        this.volatility = volatility;
        this.sharpeRatio = sharpeRatio;
        this.beta = beta;
        this.alpha = alpha;
    }

    public void setTopPerformer(String symbol, BigDecimal returnPercent) {
        this.topPerformer = symbol;
        this.topPerformerReturn = returnPercent;
    }

    public void setWorstPerformer(String symbol, BigDecimal returnPercent) {
        this.worstPerformer = symbol;
        this.worstPerformerReturn = returnPercent;
    }

    public void rebalanced() {
        this.lastRebalancedAt = LocalDateTime.now();
    }

    public boolean needsRebalancing(BigDecimal threshold) {
        // Check if any asset class deviates from target allocation by more than threshold
        return false; // Implementation depends on target allocation strategy
    }
}