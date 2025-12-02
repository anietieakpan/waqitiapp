package com.waqiti.expense.ml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ML-based expense prediction engine
 * Predicts future expenses based on historical patterns
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExpensePredictor {

    /**
     * Predict monthly expense for given category
     */
    public BigDecimal predictMonthlyExpense(String category, List<BigDecimal> historicalExpenses) {
        try {
            if (historicalExpenses == null || historicalExpenses.isEmpty()) {
                return BigDecimal.ZERO;
            }

            // Simple moving average prediction
            BigDecimal sum = historicalExpenses.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal average = sum.divide(
                BigDecimal.valueOf(historicalExpenses.size()), 
                2, 
                RoundingMode.HALF_UP
            );

            log.debug("Predicted monthly expense for category {}: {}", category, average);
            return average;

        } catch (Exception e) {
            log.error("Error predicting expenses", e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Predict budget requirement for next period
     */
    public BigDecimal predictBudgetRequirement(
            String category, 
            List<BigDecimal> historicalExpenses,
            double growthFactor) {

        BigDecimal baseAmount = predictMonthlyExpense(category, historicalExpenses);

        // Apply growth factor for buffer
        BigDecimal buffer = baseAmount.multiply(BigDecimal.valueOf(growthFactor));

        return baseAmount.add(buffer).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Detect anomalous expenses
     */
    public boolean isAnomalousExpense(BigDecimal amount, List<BigDecimal> historicalExpenses) {
        if (historicalExpenses == null || historicalExpenses.size() < 3) {
            return false; // Not enough data
        }

        // Calculate mean and standard deviation
        BigDecimal mean = predictMonthlyExpense(null, historicalExpenses);

        double stdDev = calculateStandardDeviation(historicalExpenses, mean);

        // Flag if amount is more than 2 standard deviations from mean
        double threshold = mean.doubleValue() + (2 * stdDev);

        return amount.doubleValue() > threshold;
    }

    private double calculateStandardDeviation(List<BigDecimal> values, BigDecimal mean) {
        double meanValue = mean.doubleValue();

        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v.doubleValue() - meanValue, 2))
            .average()
            .orElse(0.0);

        return Math.sqrt(variance);
    }
}
