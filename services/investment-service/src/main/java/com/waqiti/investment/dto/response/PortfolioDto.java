package com.waqiti.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioDto {

    private String id;
    private String investmentAccountId;
    private BigDecimal totalValue;
    private BigDecimal totalCost;
    private BigDecimal totalReturn;
    private BigDecimal totalReturnPercent;
    private BigDecimal dayChange;
    private BigDecimal dayChangePercent;
    private BigDecimal realizedGains;
    private BigDecimal unrealizedGains;
    private BigDecimal dividendEarnings;
    private Integer numberOfPositions;
    
    // Asset allocation
    private BigDecimal cashPercentage;
    private BigDecimal equityPercentage;
    private BigDecimal etfPercentage;
    private BigDecimal cryptoPercentage;
    
    // Risk metrics
    private BigDecimal diversificationScore;
    private BigDecimal riskScore;
    private BigDecimal volatility;
    private BigDecimal sharpeRatio;
    private BigDecimal beta;
    private BigDecimal alpha;
    
    // Performance
    private String topPerformer;
    private String worstPerformer;
    private BigDecimal topPerformerReturn;
    private BigDecimal worstPerformerReturn;
    
    // Holdings
    private List<HoldingSummaryDto> topHoldings;
    private List<AssetAllocationDto> assetAllocation;
    private List<SectorAllocationDto> sectorAllocation;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastRebalancedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingSummaryDto {
        private String symbol;
        private String name;
        private BigDecimal quantity;
        private BigDecimal marketValue;
        private BigDecimal portfolioPercentage;
        private BigDecimal dayChange;
        private BigDecimal dayChangePercent;
        private BigDecimal totalReturn;
        private BigDecimal totalReturnPercent;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetAllocationDto {
        private String assetType;
        private BigDecimal percentage;
        private BigDecimal value;
        private Integer count;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorAllocationDto {
        private String sector;
        private BigDecimal percentage;
        private BigDecimal value;
        private Integer count;
    }
    
    // Calculated methods
    public boolean isProfitable() {
        return totalReturn != null && totalReturn.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isWellDiversified() {
        return diversificationScore != null && diversificationScore.compareTo(new BigDecimal("0.7")) > 0;
    }
    
    public boolean isHighRisk() {
        return riskScore != null && riskScore.compareTo(new BigDecimal("7")) > 0;
    }
}