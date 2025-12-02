package com.waqiti.expense.service;

import com.waqiti.expense.dto.ExpenseAnalyticsDto;
import com.waqiti.expense.dto.ExpenseSummaryDto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for expense analytics and insights
 */
public interface ExpenseAnalyticsService {

    /**
     * Calculate expense summary for a period
     */
    ExpenseSummaryDto calculateExpenseSummary(UUID userId, LocalDateTime start, LocalDateTime end);

    /**
     * Generate comprehensive analytics
     */
    ExpenseAnalyticsDto generateAnalytics(UUID userId, int months);

    /**
     * Detect anomalies in spending patterns
     */
    java.util.List<ExpenseAnalyticsDto.AnomalyAlert> detectAnomalies(UUID userId);

    /**
     * Get spending predictions
     */
    java.math.BigDecimal predictNextMonthSpending(UUID userId);

    /**
     * Get spending recommendations
     */
    java.util.List<String> getSpendingRecommendations(UUID userId);
}
