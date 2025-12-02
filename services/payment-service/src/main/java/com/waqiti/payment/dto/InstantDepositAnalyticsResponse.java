package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Analytics response for instant deposits
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantDepositAnalyticsResponse {
    private OverallStats overallStats;
    private List<DailyStats> dailyStats;
    private Map<String, NetworkStats> networkStats;
    private List<TopDeposit> topDeposits;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallStats {
        private Long totalCount;
        private Long successCount;
        private Long failureCount;
        private BigDecimal totalVolume;
        private BigDecimal totalFeesCollected;
        private Double averageAmount;
        private Double successRate;
        private Double averageProcessingTime;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private LocalDateTime date;
        private Long count;
        private BigDecimal volume;
        private BigDecimal feesCollected;
        private Double successRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkStats {
        private String network;
        private Long count;
        private BigDecimal volume;
        private Double successRate;
        private Double averageFee;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopDeposit {
        private String depositId;
        private BigDecimal amount;
        private BigDecimal fee;
        private String network;
        private LocalDateTime processedAt;
    }
}