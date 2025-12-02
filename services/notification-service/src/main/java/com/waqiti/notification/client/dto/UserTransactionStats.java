package com.waqiti.notification.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTransactionStats {
    private UUID userId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String periodLabel;
    
    private Long totalTransactions;
    private BigDecimal totalVolume;
    private BigDecimal totalIncoming;
    private BigDecimal totalOutgoing;
    private String primaryCurrency;
    
    private Map<String, Long> transactionCountByType;
    private Map<String, BigDecimal> volumeByType;
    private Map<String, Long> transactionCountByStatus;
    
    private List<DailyStats> dailyStats;
    private List<CategoryStats> categoryStats;
    private List<MerchantStats> merchantStats;
    
    private BigDecimal averageTransactionSize;
    private BigDecimal medianTransactionSize;
    private BigDecimal largestTransaction;
    private BigDecimal smallestTransaction;
    
    private Integer peakTransactionHour;
    private String peakTransactionDay;
    private Map<Integer, Long> transactionsByHour;
    private Map<String, Long> transactionsByDayOfWeek;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private LocalDateTime date;
        private Long transactionCount;
        private BigDecimal totalVolume;
        private BigDecimal netFlow;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStats {
        private String category;
        private Long transactionCount;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
        private Double percentOfTotal;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantStats {
        private String merchantId;
        private String merchantName;
        private Long transactionCount;
        private BigDecimal totalAmount;
        private LocalDateTime lastTransaction;
    }
}