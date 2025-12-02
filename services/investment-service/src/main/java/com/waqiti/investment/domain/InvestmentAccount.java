package com.waqiti.investment.domain;

import com.waqiti.investment.domain.enums.AccountStatus;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "investment_accounts", indexes = {
    // CRITICAL: Primary access patterns for performance and preventing full table scans
    @Index(name = "idx_investment_customer_id", columnList = "customerId"),
    @Index(name = "idx_investment_account_number", columnList = "accountNumber", unique = true),
    @Index(name = "idx_investment_wallet_account", columnList = "walletAccountId"),
    @Index(name = "idx_investment_status", columnList = "status"),

    // CRITICAL: Composite indexes for common query patterns
    @Index(name = "idx_investment_customer_status", columnList = "customerId, status"),
    @Index(name = "idx_investment_status_kyc", columnList = "status, kycVerified"),
    @Index(name = "idx_investment_brokerage", columnList = "brokerageProvider, brokerageAccountId"),

    // Performance: Reporting and analytics queries
    @Index(name = "idx_investment_created_at", columnList = "createdAt"),
    @Index(name = "idx_investment_updated_at", columnList = "updatedAt"),
    @Index(name = "idx_investment_last_activity", columnList = "lastActivityAt"),
    @Index(name = "idx_investment_activated_at", columnList = "activatedAt"),

    // Pattern Day Trading compliance queries
    @Index(name = "idx_investment_pattern_trader", columnList = "patternDayTrader, dayTrades")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"portfolio", "orders", "holdings", "transfers"})
@ToString(exclude = {"portfolio", "orders", "holdings", "transfers"})
@EntityListeners(AuditingEntityListener.class)
public class InvestmentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private String walletAccountId;

    // Financial amounts: precision=19, scale=4 for monetary values
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal investedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dayChange = BigDecimal.ZERO;

    // Percentages: precision=5, scale=2 for percentage values (e.g., 99.99%)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal dayChangePercent = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalReturn = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal totalReturnPercent = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedGains = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unrealizedGains = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dividendEarnings = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.PENDING_ACTIVATION;

    @Column(nullable = false)
    private Boolean kycVerified = false;

    @Column(nullable = false)
    private Boolean patternDayTrader = false;

    @Column(nullable = false)
    private Integer dayTrades = 0;

    private String riskProfile;

    private String investmentGoals;

    @Column(precision = 5, scale = 2)
    private BigDecimal riskTolerance;

    @Column(name = "brokerage_account_id")
    private String brokerageAccountId;

    @Column(name = "brokerage_provider")
    private String brokerageProvider;

    @OneToOne(mappedBy = "investmentAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Portfolio portfolio;

    @OneToMany(mappedBy = "investmentAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvestmentOrder> orders = new ArrayList<>();

    @OneToMany(mappedBy = "investmentAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvestmentHolding> holdings = new ArrayList<>();

    @OneToMany(mappedBy = "investmentAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transfer> transfers = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime activatedAt;

    private LocalDateTime lastActivityAt;

    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public void updateBalances(BigDecimal cashBalance, BigDecimal totalValue) {
        this.cashBalance = cashBalance;
        this.totalValue = totalValue;
        this.lastActivityAt = LocalDateTime.now();
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
    }

    public void close() {
        this.status = AccountStatus.CLOSED;
    }

    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(this.status);
    }

    public boolean canTrade() {
        return isActive() && kycVerified;
    }

    public void updateDayChange(BigDecimal dayChange, BigDecimal dayChangePercent) {
        this.dayChange = dayChange;
        this.dayChangePercent = dayChangePercent;
    }

    public void updateTotalReturn(BigDecimal totalReturn, BigDecimal totalReturnPercent) {
        this.totalReturn = totalReturn;
        this.totalReturnPercent = totalReturnPercent;
    }

    public void updateGains(BigDecimal realizedGains, BigDecimal unrealizedGains) {
        this.realizedGains = realizedGains;
        this.unrealizedGains = unrealizedGains;
    }

    public void addDividendEarnings(BigDecimal amount) {
        this.dividendEarnings = this.dividendEarnings.add(amount);
    }

    public void incrementDayTrades() {
        this.dayTrades++;
        if (this.dayTrades >= 4) {
            this.patternDayTrader = true;
        }
    }

    public void resetDayTrades() {
        this.dayTrades = 0;
    }
}