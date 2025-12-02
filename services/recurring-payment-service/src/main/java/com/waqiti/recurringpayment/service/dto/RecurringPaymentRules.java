package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Recurring Payment Rules DTO
 * Defines fraud detection rules for recurring payments
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringPaymentRules {
    private BigDecimal maxDailyAmount; // added from old refactored code - aniix
    private BigDecimal maxMonthlyAmount; // added from old refactored code - aniix
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    private int maxActiveRecurring;
    private int maxDailyExecutions;
    private List<String> blockedCountries;
    private List<String> highRiskCategories;
    private Map<String, Object> additionalRules;
    private BigDecimal requiresReviewAbove; // added from old refactored code - aniix
    private boolean fallbackMode; // added from old refactored code - aniix
}
