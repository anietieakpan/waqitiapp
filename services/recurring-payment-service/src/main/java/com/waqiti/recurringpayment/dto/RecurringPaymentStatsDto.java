package com.waqiti.recurringpayment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class RecurringPaymentStatsDto {
    private int totalActiveRecurring;
    private int totalPausedRecurring;
    private int totalCompletedRecurring;
    private int totalCancelledRecurring;
    
    private BigDecimal totalMonthlyCommitment;
    private BigDecimal totalYearlyCommitment;
    private BigDecimal totalAmountPaidThisMonth;
    private BigDecimal totalAmountPaidThisYear;
    
    private int executionsThisMonth;
    private int successfulExecutionsThisMonth;
    private int failedExecutionsThisMonth;
    private double overallSuccessRate;
    
    private Map<String, Integer> recurringByFrequency;
    private Map<String, BigDecimal> commitmentByCurrency;
    private Map<String, Integer> recurringByCategory;
    
    private int upcomingPaymentsNext7Days;
    private int upcomingPaymentsNext30Days;
    private BigDecimal upcomingAmountNext7Days;
    private BigDecimal upcomingAmountNext30Days;
}