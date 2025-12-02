package com.waqiti.dispute.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeStatistics {

    private Long totalDisputes;
    private Long openDisputes;
    private Long closedDisputes;
    private Long resolvedInFavorOfCustomer;
    private Long resolvedInFavorOfMerchant;
    private BigDecimal totalDisputedAmount;
    private BigDecimal totalRefundedAmount;
    private Double averageResolutionTimeDays;
    private Double customerWinRate;
    private Map<String, Long> disputesByType;
    private Map<String, Long> disputesByStatus;
    private Map<String, BigDecimal> amountByType;

    private double averageResolutionTimeHours; // added by aniix from old refactoring exercise
    private long resolvedDisputes; // added by aniix from old refactoring exercise
}
