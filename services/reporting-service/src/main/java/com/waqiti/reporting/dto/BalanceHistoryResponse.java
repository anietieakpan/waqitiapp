package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceHistoryResponse {

    private UUID accountId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String currency;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal minimumBalance;
    private BigDecimal maximumBalance;
    private BigDecimal averageBalance;
    private List<DailyBalance> dailyBalances;
    private BalanceStatistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyBalance {
        private LocalDate date;
        private BigDecimal openingBalance;
        private BigDecimal closingBalance;
        private BigDecimal minimumBalance;
        private BigDecimal maximumBalance;
        private BigDecimal totalDebits;
        private BigDecimal totalCredits;
        private Integer transactionCount;
        private LocalDateTime lastTransactionTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceStatistics {
        private BigDecimal standardDeviation;
        private BigDecimal variance;
        private Integer daysWithPositiveBalance;
        private Integer daysWithNegativeBalance;
        private Integer daysWithZeroBalance;
        private BigDecimal highestSingleDayIncrease;
        private BigDecimal highestSingleDayDecrease;
        private LocalDate highestBalanceDate;
        private LocalDate lowestBalanceDate;
        private Double volatilityIndex;
    }
}