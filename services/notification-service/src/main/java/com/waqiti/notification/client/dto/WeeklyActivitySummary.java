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
public class WeeklyActivitySummary {
    private UUID userId;
    private LocalDateTime weekStart;
    private LocalDateTime weekEnd;
    private Integer weekNumber;
    private Integer year;
    
    private Integer transactionCount;
    private BigDecimal totalSpent;
    private BigDecimal totalReceived;
    private String primaryCurrency;
    private String formattedTotalSpent;
    private String formattedTotalReceived;
    
    private Map<String, Integer> transactionsByDay;
    private Map<String, BigDecimal> spendingByCategory;
    private List<TopMerchant> topMerchants;
    private List<TransactionHighlight> highlights;
    
    private BigDecimal averageDailySpend;
    private BigDecimal weeklyBudget;
    private BigDecimal budgetUtilization;
    private String budgetStatus;
    
    private ComparisonData comparisonWithLastWeek;
    private List<String> insights;
    private List<String> recommendations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopMerchant {
        private String merchantName;
        private String merchantCategory;
        private Integer transactionCount;
        private BigDecimal totalAmount;
        private String formattedAmount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionHighlight {
        private String type;
        private String description;
        private BigDecimal amount;
        private LocalDateTime date;
        private String merchantName;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonData {
        private BigDecimal spentChangePercent;
        private BigDecimal receivedChangePercent;
        private Integer transactionCountChange;
        private String trend;
        private String summary;
    }
}