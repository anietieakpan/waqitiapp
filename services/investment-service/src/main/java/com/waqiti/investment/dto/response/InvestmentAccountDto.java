package com.waqiti.investment.dto.response;

import com.waqiti.investment.domain.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentAccountDto {

    private String id;
    private String customerId;
    private String accountNumber;
    private String walletAccountId;
    private BigDecimal cashBalance;
    private BigDecimal investedAmount;
    private BigDecimal totalValue;
    private BigDecimal dayChange;
    private BigDecimal dayChangePercent;
    private BigDecimal totalReturn;
    private BigDecimal totalReturnPercent;
    private BigDecimal realizedGains;
    private BigDecimal unrealizedGains;
    private BigDecimal dividendEarnings;
    private AccountStatus status;
    private Boolean kycVerified;
    private Boolean patternDayTrader;
    private Integer dayTrades;
    private String riskProfile;
    private String investmentGoals;
    private BigDecimal riskTolerance;
    private String brokerageAccountId;
    private String brokerageProvider;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime lastActivityAt;
    
    // Summary fields
    private Integer numberOfPositions;
    private BigDecimal portfolioDiversification;
    private BigDecimal accountPerformance;
    private Boolean hasActiveOrders;
    private Boolean hasAutoInvest;
    
    // Calculated fields
    public BigDecimal getAvailableCash() {
        return cashBalance != null ? cashBalance : BigDecimal.ZERO;
    }
    
    public BigDecimal getTotalGains() {
        BigDecimal realized = realizedGains != null ? realizedGains : BigDecimal.ZERO;
        BigDecimal unrealized = unrealizedGains != null ? unrealizedGains : BigDecimal.ZERO;
        return realized.add(unrealized);
    }
    
    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(status);
    }
    
    public boolean canTrade() {
        return isActive() && Boolean.TRUE.equals(kycVerified);
    }
}