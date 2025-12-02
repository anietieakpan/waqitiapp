package com.waqiti.analytics.dto.model.validation;

import com.waqiti.analytics.dto.model.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Comprehensive validators for spending analytics DTOs.
 * 
 * This class provides domain-specific validation logic that goes beyond basic
 * Jakarta Bean Validation constraints. It implements business rules, data consistency
 * checks, and complex validation scenarios specific to financial analytics.
 * 
 * <p>Features:
 * <ul>
 *   <li>Business rule validation for financial data</li>
 *   <li>Date range and temporal consistency validation</li>
 *   <li>Amount and percentage validation with business context</li>
 *   <li>Cross-field validation and data integrity checks</li>
 *   <li>Performance-optimized validation methods</li>
 *   <li>Detailed error reporting and diagnostics</li>
 * </ul>
 * 
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class SpendingValidators {

    // Validation constants
    private static final BigDecimal MAX_REASONABLE_AMOUNT = new BigDecimal("1000000.00");
    private static final BigDecimal MIN_REASONABLE_AMOUNT = BigDecimal.ZERO;
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100.0");
    private static final int MAX_HISTORICAL_DAYS = 2555; // ~7 years
    private static final int MAX_FUTURE_DAYS = 365; // 1 year
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern MERCHANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,100}$");

    /**
     * Validation result container
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }

    /**
     * Validates a CategorySpending DTO
     * 
     * @param categorySpending the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateCategorySpending(CategorySpending categorySpending) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (categorySpending == null) {
            errors.add("CategorySpending cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Basic field validation
        validateRequiredString(categorySpending.getCategoryName(), "categoryName", errors);
        validateAmount(categorySpending.getAmount(), "amount", errors, warnings);
        validateTransactionCount(categorySpending.getTransactionCount(), errors);

        // Business logic validation
        validatePercentage(categorySpending.getPercentage(), "percentage", errors, warnings);
        validateAverageCalculation(categorySpending.getAmount(), categorySpending.getTransactionCount(), 
            categorySpending.getAveragePerTransaction(), "averagePerTransaction", errors, warnings);

        // Temporal validation
        validateDateRange(categorySpending.getPeriodStart(), categorySpending.getPeriodEnd(), errors);
        
        // Currency validation
        validateCurrency(categorySpending.getCurrency(), warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a DailySpending DTO
     * 
     * @param dailySpending the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateDailySpending(DailySpending dailySpending) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (dailySpending == null) {
            errors.add("DailySpending cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Date validation
        validateDate(dailySpending.getDate(), "date", errors);
        
        // Amount validation
        validateAmount(dailySpending.getAmount(), "amount", errors, warnings);
        validateTransactionCount(dailySpending.getTransactionCount(), errors);

        // Day-specific validation
        validateDayOfWeekConsistency(dailySpending.getDate(), dailySpending.getDayOfWeek(), errors);
        validateWeekendConsistency(dailySpending.getDayOfWeek(), dailySpending.getIsWeekend(), errors);
        
        // Rolling average validation
        validateRollingAverage(dailySpending.getAmount(), dailySpending.getRolling7DayAverage(), 
            "rolling7DayAverage", warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates an HourlySpending DTO
     * 
     * @param hourlySpending the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateHourlySpending(HourlySpending hourlySpending) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (hourlySpending == null) {
            errors.add("HourlySpending cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Hour validation
        validateHour(hourlySpending.getHour(), errors);
        
        // Amount and transaction validation
        validateAmount(hourlySpending.getAmount(), "amount", errors, warnings);
        validateTransactionCount(hourlySpending.getTransactionCount(), errors);

        // Timezone validation
        validateTimezone(hourlySpending.getTimezone(), warnings);
        
        // Time slot consistency
        validateTimeSlotConsistency(hourlySpending.getHour(), hourlySpending.getTimeSlot(), warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a WeeklySpending DTO
     * 
     * @param weeklySpending the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateWeeklySpending(WeeklySpending weeklySpending) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (weeklySpending == null) {
            errors.add("WeeklySpending cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // ISO week validation
        validateISOWeek(weeklySpending.getIsoWeek(), weeklySpending.getIsoYear(), errors);
        
        // Date range validation
        validateWeekDateRange(weeklySpending.getWeekStartDate(), weeklySpending.getWeekEndDate(), errors);
        
        // Amount validation
        validateAmount(weeklySpending.getAmount(), "amount", errors, warnings);
        
        // Weekday/weekend breakdown validation
        validateWeekdayWeekendBreakdown(weeklySpending.getAmount(), 
            weeklySpending.getWeekdaySpending(), weeklySpending.getWeekendSpending(), errors, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a MonthlySpending DTO
     * 
     * @param monthlySpending the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateMonthlySpending(MonthlySpending monthlySpending) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (monthlySpending == null) {
            errors.add("MonthlySpending cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Month and year validation
        validateMonthYear(monthlySpending.getMonth(), monthlySpending.getYear(), errors);
        
        // Monthly date range validation
        validateMonthlyDateRange(monthlySpending.getMonthStartDate(), monthlySpending.getMonthEndDate(), 
            monthlySpending.getYear(), monthlySpending.getMonth(), errors);

        // Budget validation
        validateBudgetData(monthlySpending.getAmount(), monthlySpending.getBudgetAmount(), 
            monthlySpending.getBudgetVariance(), monthlySpending.getIsOverBudget(), errors, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a SpendingTrend DTO
     * 
     * @param spendingTrend the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateSpendingTrend(SpendingTrend spendingTrend) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (spendingTrend == null) {
            errors.add("SpendingTrend cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Analysis period validation
        validateDateRange(spendingTrend.getAnalysisStartDate(), spendingTrend.getAnalysisEndDate(), errors);
        
        // Statistical validation
        validateStatisticalValues(spendingTrend, errors, warnings);
        
        // Confidence validation
        validateConfidence(spendingTrend.getConfidence(), errors);
        
        // Prediction validation
        validatePredictions(spendingTrend, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a SpendingAlert DTO
     * 
     * @param spendingAlert the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateSpendingAlert(SpendingAlert spendingAlert) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (spendingAlert == null) {
            errors.add("SpendingAlert cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // ID validation
        validateId(spendingAlert.getAlertId(), "alertId", errors);
        validateId(spendingAlert.getUserId(), "userId", errors);
        
        // Threshold validation
        validateAlertThresholds(spendingAlert, errors);
        
        // Notification validation
        validateNotificationSettings(spendingAlert, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a SpendingInsight DTO
     * 
     * @param spendingInsight the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateSpendingInsight(SpendingInsight spendingInsight) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (spendingInsight == null) {
            errors.add("SpendingInsight cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Basic validation
        validateId(spendingInsight.getInsightId(), "insightId", errors);
        validateId(spendingInsight.getUserId(), "userId", errors);
        validateRequiredString(spendingInsight.getTitle(), "title", errors);
        validateRequiredString(spendingInsight.getDescription(), "description", errors);
        
        // Confidence validation
        validateConfidence(spendingInsight.getConfidence(), errors);
        
        // Impact validation
        validateImpactScore(spendingInsight.getImpactScore(), warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a MerchantSpending DTO
     * 
     * @param merchantSpending the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateMerchantSpending(MerchantSpending merchantSpending) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (merchantSpending == null) {
            errors.add("MerchantSpending cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // Merchant validation
        validateMerchantId(merchantSpending.getMerchantId(), errors);
        validateRequiredString(merchantSpending.getMerchantName(), "merchantName", errors);
        
        // Relationship validation
        validateMerchantRelationship(merchantSpending, warnings);
        
        // Loyalty validation
        validateLoyaltyMetrics(merchantSpending, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates a SpendingComparison DTO
     * 
     * @param spendingComparison the DTO to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validateSpendingComparison(SpendingComparison spendingComparison) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (spendingComparison == null) {
            errors.add("SpendingComparison cannot be null");
            return new ValidationResult(false, errors, warnings);
        }

        // User validation
        validateId(spendingComparison.getUserId(), "userId", errors);
        validateAmount(spendingComparison.getUserAmount(), "userAmount", errors, warnings);
        
        // Peer group validation
        validatePeerGroupData(spendingComparison, errors, warnings);
        
        // Statistical validation
        validateComparisonStatistics(spendingComparison, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // Private helper methods

    private void validateRequiredString(String value, String fieldName, List<String> errors) {
        if (!StringUtils.hasText(value)) {
            errors.add(fieldName + " is required and cannot be empty");
        }
    }

    private void validateAmount(BigDecimal amount, String fieldName, List<String> errors, List<String> warnings) {
        if (amount == null) {
            errors.add(fieldName + " cannot be null");
            return;
        }
        
        if (amount.compareTo(MIN_REASONABLE_AMOUNT) < 0) {
            errors.add(fieldName + " cannot be negative");
        }
        
        if (amount.compareTo(MAX_REASONABLE_AMOUNT) > 0) {
            warnings.add(fieldName + " is unusually large: " + amount);
        }
    }

    private void validateTransactionCount(long count, List<String> errors) {
        if (count < 0) {
            errors.add("Transaction count cannot be negative");
        }
    }

    private void validatePercentage(BigDecimal percentage, String fieldName, List<String> errors, List<String> warnings) {
        if (percentage == null) return;
        
        if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(fieldName + " cannot be negative");
        }
        
        if (percentage.compareTo(MAX_PERCENTAGE) > 0) {
            warnings.add(fieldName + " exceeds 100%: " + percentage);
        }
    }

    private void validateDate(LocalDate date, String fieldName, List<String> errors) {
        if (date == null) {
            errors.add(fieldName + " cannot be null");
            return;
        }
        
        LocalDate now = LocalDate.now();
        if (date.isAfter(now.plusDays(MAX_FUTURE_DAYS))) {
            errors.add(fieldName + " is too far in the future");
        }
        
        if (date.isBefore(now.minusDays(MAX_HISTORICAL_DAYS))) {
            errors.add(fieldName + " is too far in the past");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate, List<String> errors) {
        if (startDate == null || endDate == null) return;
        
        if (startDate.isAfter(endDate)) {
            errors.add("Start date cannot be after end date");
        }
        
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > MAX_HISTORICAL_DAYS) {
            errors.add("Date range is too large: " + daysBetween + " days");
        }
    }

    private void validateHour(Integer hour, List<String> errors) {
        if (hour == null) {
            errors.add("Hour cannot be null");
            return;
        }
        
        if (hour < 0 || hour > 23) {
            errors.add("Hour must be between 0 and 23, got: " + hour);
        }
    }

    private void validateCurrency(String currency, List<String> warnings) {
        if (currency != null && !CURRENCY_PATTERN.matcher(currency).matches()) {
            warnings.add("Currency code should be a 3-letter ISO code: " + currency);
        }
    }

    private void validateTimezone(String timezone, List<String> warnings) {
        if (StringUtils.hasText(timezone)) {
            try {
                java.time.ZoneId.of(timezone);
            } catch (Exception e) {
                warnings.add("Invalid timezone: " + timezone);
            }
        }
    }

    private void validateDayOfWeekConsistency(LocalDate date, DayOfWeek dayOfWeek, List<String> errors) {
        if (date != null && dayOfWeek != null && !date.getDayOfWeek().equals(dayOfWeek)) {
            errors.add("Day of week inconsistency: " + date + " is " + date.getDayOfWeek() + " but field shows " + dayOfWeek);
        }
    }

    private void validateWeekendConsistency(DayOfWeek dayOfWeek, Boolean isWeekend, List<String> errors) {
        if (dayOfWeek != null && isWeekend != null) {
            boolean shouldBeWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            if (shouldBeWeekend != isWeekend) {
                errors.add("Weekend flag inconsistency for " + dayOfWeek);
            }
        }
    }

    private void validateTimeSlotConsistency(Integer hour, HourlySpending.TimeSlot timeSlot, List<String> warnings) {
        if (hour != null && timeSlot != null) {
            HourlySpending.TimeSlot expectedSlot = HourlySpending.TimeSlot.fromHour(hour);
            if (!expectedSlot.equals(timeSlot)) {
                warnings.add("Time slot inconsistency: hour " + hour + " should be " + expectedSlot + " but got " + timeSlot);
            }
        }
    }

    private void validateISOWeek(Integer isoWeek, Integer isoYear, List<String> errors) {
        if (isoWeek == null || isoYear == null) {
            errors.add("ISO week and year are required");
            return;
        }
        
        if (isoWeek < 1 || isoWeek > 53) {
            errors.add("ISO week must be between 1 and 53, got: " + isoWeek);
        }
        
        if (isoYear < 1900 || isoYear > 2100) {
            errors.add("ISO year is out of reasonable range: " + isoYear);
        }
    }

    private void validateWeekDateRange(LocalDate weekStart, LocalDate weekEnd, List<String> errors) {
        if (weekStart == null || weekEnd == null) return;
        
        if (!weekStart.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            errors.add("Week start date must be a Monday");
        }
        
        if (!weekEnd.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
            errors.add("Week end date must be a Sunday");
        }
        
        if (ChronoUnit.DAYS.between(weekStart, weekEnd) != 6) {
            errors.add("Week must span exactly 7 days");
        }
    }

    private void validateWeekdayWeekendBreakdown(BigDecimal total, BigDecimal weekday, BigDecimal weekend, 
            List<String> errors, List<String> warnings) {
        if (total == null) return;
        
        if (weekday != null && weekend != null) {
            BigDecimal sum = weekday.add(weekend);
            if (sum.compareTo(total) != 0) {
                BigDecimal difference = total.subtract(sum).abs();
                if (difference.compareTo(new BigDecimal("0.01")) > 0) {
                    warnings.add("Weekday + weekend spending doesn't equal total: " + weekday + " + " + weekend + " != " + total);
                }
            }
        }
    }

    private void validateMonthYear(Integer month, Integer year, List<String> errors) {
        if (month == null || year == null) {
            errors.add("Month and year are required");
            return;
        }
        
        if (month < 1 || month > 12) {
            errors.add("Month must be between 1 and 12, got: " + month);
        }
        
        if (year < 1900 || year > 2100) {
            errors.add("Year is out of reasonable range: " + year);
        }
    }

    private void validateMonthlyDateRange(LocalDate monthStart, LocalDate monthEnd, Integer year, Integer month, List<String> errors) {
        if (monthStart == null || monthEnd == null || year == null || month == null) return;
        
        if (monthStart.getDayOfMonth() != 1) {
            errors.add("Month start date must be the first day of the month");
        }
        
        if (monthStart.getMonthValue() != month || monthStart.getYear() != year) {
            errors.add("Month start date doesn't match year/month fields");
        }
        
        if (!monthEnd.equals(monthStart.withDayOfMonth(monthStart.lengthOfMonth()))) {
            errors.add("Month end date must be the last day of the month");
        }
    }

    private void validateBudgetData(BigDecimal amount, BigDecimal budgetAmount, BigDecimal budgetVariance, 
            Boolean isOverBudget, List<String> errors, List<String> warnings) {
        if (amount == null) return;
        
        if (budgetAmount != null && isOverBudget != null) {
            boolean shouldBeOverBudget = amount.compareTo(budgetAmount) > 0;
            if (shouldBeOverBudget != isOverBudget) {
                warnings.add("Over budget flag inconsistency");
            }
        }
        
        if (budgetAmount != null && budgetVariance != null) {
            // Validate calculated variance matches provided variance
            BigDecimal calculatedVariance = amount.subtract(budgetAmount)
                .divide(budgetAmount, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            if (calculatedVariance.subtract(budgetVariance).abs().compareTo(new BigDecimal("0.1")) > 0) {
                warnings.add("Budget variance calculation inconsistency");
            }
        }
    }

    private void validateAverageCalculation(BigDecimal total, long count, BigDecimal average, String fieldName, 
            List<String> errors, List<String> warnings) {
        if (total == null || average == null || count == 0) return;
        
        BigDecimal calculatedAverage = total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal difference = calculatedAverage.subtract(average).abs();
        
        if (difference.compareTo(new BigDecimal("0.01")) > 0) {
            warnings.add(fieldName + " calculation inconsistency: expected " + calculatedAverage + ", got " + average);
        }
    }

    private void validateRollingAverage(BigDecimal current, BigDecimal rollingAverage, String fieldName, List<String> warnings) {
        if (current == null || rollingAverage == null) return;
        
        // Check if current amount is extremely different from rolling average
        BigDecimal ratio = current.divide(rollingAverage, 2, java.math.RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("5.0")) > 0 || ratio.compareTo(new BigDecimal("0.2")) < 0) {
            warnings.add(fieldName + " shows unusual deviation from current amount");
        }
    }

    private void validateStatisticalValues(SpendingTrend trend, List<String> errors, List<String> warnings) {
        // Correlation validation
        if (trend.getCorrelation() != null) {
            BigDecimal corr = trend.getCorrelation();
            if (corr.compareTo(new BigDecimal("-1.0")) < 0 || corr.compareTo(BigDecimal.ONE) > 0) {
                errors.add("Correlation must be between -1 and 1, got: " + corr);
            }
        }
        
        // R-squared validation
        if (trend.getRSquared() != null) {
            BigDecimal rSquared = trend.getRSquared();
            if (rSquared.compareTo(BigDecimal.ZERO) < 0 || rSquared.compareTo(BigDecimal.ONE) > 0) {
                errors.add("R-squared must be between 0 and 1, got: " + rSquared);
            }
        }
        
        // Standard deviation validation
        if (trend.getStandardDeviation() != null && trend.getStandardDeviation().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Standard deviation cannot be negative");
        }
    }

    private void validateConfidence(BigDecimal confidence, List<String> errors) {
        if (confidence == null) {
            errors.add("Confidence cannot be null");
            return;
        }
        
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            errors.add("Confidence must be between 0 and 1, got: " + confidence);
        }
    }

    private void validatePredictions(SpendingTrend trend, List<String> warnings) {
        if (trend.getPredictedNextPeriod() != null && trend.getPredictionLowerBound() != null && trend.getPredictionUpperBound() != null) {
            BigDecimal predicted = trend.getPredictedNextPeriod();
            BigDecimal lower = trend.getPredictionLowerBound();
            BigDecimal upper = trend.getPredictionUpperBound();
            
            if (predicted.compareTo(lower) < 0 || predicted.compareTo(upper) > 0) {
                warnings.add("Predicted value is outside confidence interval");
            }
            
            if (lower.compareTo(upper) > 0) {
                warnings.add("Lower bound is greater than upper bound");
            }
        }
    }

    private void validateId(String id, String fieldName, List<String> errors) {
        if (!StringUtils.hasText(id)) {
            errors.add(fieldName + " is required");
            return;
        }
        
        if (!USER_ID_PATTERN.matcher(id).matches()) {
            errors.add(fieldName + " contains invalid characters");
        }
    }

    private void validateMerchantId(String merchantId, List<String> errors) {
        if (!StringUtils.hasText(merchantId)) {
            errors.add("Merchant ID is required");
            return;
        }
        
        if (!MERCHANT_ID_PATTERN.matcher(merchantId).matches()) {
            errors.add("Merchant ID contains invalid characters");
        }
    }

    private void validateAlertThresholds(SpendingAlert alert, List<String> errors) {
        if (alert.getThresholdAmount() == null && alert.getThresholdPercentage() == null) {
            errors.add("At least one threshold (amount or percentage) must be specified");
        }
    }

    private void validateNotificationSettings(SpendingAlert alert, List<String> warnings) {
        if (alert.getCooldownMinutes() != null && alert.getCooldownMinutes() < 0) {
            warnings.add("Cooldown period cannot be negative");
        }
        
        if (alert.getNotificationChannels() != null && alert.getNotificationChannels().isEmpty()) {
            warnings.add("No notification channels specified");
        }
    }

    private void validateImpactScore(BigDecimal impactScore, List<String> warnings) {
        if (impactScore != null && (impactScore.compareTo(BigDecimal.ZERO) < 0 || impactScore.compareTo(BigDecimal.TEN) > 0)) {
            warnings.add("Impact score should be between 0 and 10, got: " + impactScore);
        }
    }

    private void validateMerchantRelationship(MerchantSpending merchant, List<String> warnings) {
        if (merchant.getFirstTransactionDate() != null && merchant.getLastTransactionDate() != null) {
            if (merchant.getFirstTransactionDate().isAfter(merchant.getLastTransactionDate())) {
                warnings.add("First transaction date is after last transaction date");
            }
        }
        
        if (merchant.getDaysSinceLastTransaction() != null && merchant.getDaysSinceLastTransaction() < 0) {
            warnings.add("Days since last transaction cannot be negative");
        }
    }

    private void validateLoyaltyMetrics(MerchantSpending merchant, List<String> warnings) {
        if (merchant.getFrequencyScore() != null) {
            BigDecimal score = merchant.getFrequencyScore();
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0) {
                warnings.add("Frequency score should be between 0 and 10, got: " + score);
            }
        }
        
        if (merchant.getSpendingPercentage() != null && merchant.getSpendingPercentage().compareTo(MAX_PERCENTAGE) > 0) {
            warnings.add("Spending percentage exceeds 100%: " + merchant.getSpendingPercentage());
        }
    }

    private void validatePeerGroupData(SpendingComparison comparison, List<String> errors, List<String> warnings) {
        if (comparison.getSampleSize() != null && comparison.getSampleSize() < 10) {
            warnings.add("Sample size is too small for reliable comparison: " + comparison.getSampleSize());
        }
        
        if (comparison.getPercentileRank() != null) {
            int rank = comparison.getPercentileRank();
            if (rank < 0 || rank > 100) {
                errors.add("Percentile rank must be between 0 and 100, got: " + rank);
            }
        }
    }

    private void validateComparisonStatistics(SpendingComparison comparison, List<String> warnings) {
        // Validate percentile ordering
        if (comparison.getPeerGroup25thPercentile() != null && comparison.getPeerGroupMedian() != null && 
            comparison.getPeerGroup75thPercentile() != null) {
            
            BigDecimal p25 = comparison.getPeerGroup25thPercentile();
            BigDecimal median = comparison.getPeerGroupMedian();
            BigDecimal p75 = comparison.getPeerGroup75thPercentile();
            
            if (p25.compareTo(median) > 0 || median.compareTo(p75) > 0) {
                warnings.add("Percentile values are not in proper order");
            }
        }
    }
}