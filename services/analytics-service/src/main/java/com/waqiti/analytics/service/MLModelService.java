package com.waqiti.analytics.service;

import com.waqiti.analytics.domain.*;
import com.waqiti.analytics.dto.response.*;
import com.waqiti.analytics.repository.TransactionAnalyticsRepository;
import com.waqiti.common.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MLModelService {

    private final TransactionAnalyticsRepository analyticsRepository;
    private final CacheService cacheService;

    public SpendingForecast predictFutureSpending(UUID userId, int daysAhead) {
        log.info("Generating spending forecast for user: {} for {} days ahead", userId, daysAhead);

        // Get historical spending data for model training
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(90); // Use 90 days of history

        List<DailySpending> historicalData = analyticsRepository.getDailySpending(userId, startDate, endDate);
        
        if (historicalData.size() < 14) {
            log.warn("Insufficient historical data for prediction. User: {}, Data points: {}", userId, historicalData.size());
            return createDefaultSpendingForecast(userId, daysAhead);
        }

        // Apply time series forecasting (simplified ARIMA-like approach)
        List<SpendingPrediction> predictions = generateSpendingPredictions(historicalData, daysAhead);
        
        // Calculate trend and seasonality
        SpendingTrend trend = calculateForecastTrend(predictions);
        List<SeasonalFactor> seasonalFactors = calculateSeasonalFactors(historicalData);
        
        // Calculate confidence intervals
        BigDecimal confidenceLevel = calculateForecastConfidence(historicalData, predictions);
        
        // Identify risk factors
        List<RiskFactor> riskFactors = identifySpendingRiskFactors(historicalData, predictions);

        return SpendingForecast.builder()
                .userId(userId)
                .forecastPeriodDays(daysAhead)
                .predictions(predictions)
                .totalPredictedSpending(predictions.stream()
                        .map(SpendingPrediction::getPredictedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .averageDailySpending(predictions.stream()
                        .map(SpendingPrediction::getPredictedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(daysAhead), 2, RoundingMode.HALF_UP))
                .trend(trend)
                .seasonalFactors(seasonalFactors)
                .confidenceLevel(confidenceLevel)
                .riskFactors(riskFactors)
                .modelAccuracy(calculateModelAccuracy(userId))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public IncomeForecast predictFutureIncome(UUID userId, int daysAhead) {
        log.info("Generating income forecast for user: {} for {} days ahead", userId, daysAhead);

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(90);

        List<DailyIncome> historicalIncome = analyticsRepository.getDailyIncome(userId, startDate, endDate);
        
        if (historicalIncome.size() < 14) {
            log.warn("Insufficient income history for prediction. User: {}", userId);
            return createDefaultIncomeForecast(userId, daysAhead);
        }

        List<IncomePrediction> predictions = generateIncomePredictions(historicalIncome, daysAhead);
        
        IncomeStability stability = analyzeIncomeStability(historicalIncome);
        List<IncomeSource> predictedSources = predictIncomeSources(userId, historicalIncome);
        
        BigDecimal confidenceLevel = calculateIncomeConfidence(historicalIncome, predictions);

        return IncomeForecast.builder()
                .userId(userId)
                .forecastPeriodDays(daysAhead)
                .predictions(predictions)
                .totalPredictedIncome(predictions.stream()
                        .map(IncomePrediction::getPredictedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .averageDailyIncome(predictions.stream()
                        .map(IncomePrediction::getPredictedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(daysAhead), 2, RoundingMode.HALF_UP))
                .stability(stability)
                .predictedSources(predictedSources)
                .confidenceLevel(confidenceLevel)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public List<CategoryTrend> predictCategoryTrends(UUID userId, int daysAhead) {
        log.info("Predicting category trends for user: {} for {} days ahead", userId, daysAhead);

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(60);

        List<CategorySpending> categoryHistory = analyticsRepository.getCategorySpending(userId, startDate, endDate);
        
        Map<String, List<CategorySpending>> categoryGroups = categoryHistory.stream()
                .collect(Collectors.groupingBy(CategorySpending::getCategory));

        return categoryGroups.entrySet().stream()
                .map(entry -> {
                    String category = entry.getKey();
                    List<CategorySpending> categoryData = entry.getValue();
                    
                    CategoryTrendPrediction prediction = predictCategoryTrend(categoryData, daysAhead);
                    
                    return CategoryTrend.builder()
                            .category(category)
                            .currentTrend(prediction.getTrend())
                            .predictedChange(prediction.getPredictedChange())
                            .confidenceLevel(prediction.getConfidence())
                            .expectedSpending(prediction.getExpectedSpending())
                            .riskLevel(prediction.getRiskLevel())
                            .recommendations(generateCategoryRecommendations(category, prediction))
                            .build();
                })
                .sorted((a, b) -> b.getExpectedSpending().compareTo(a.getExpectedSpending()))
                .collect(Collectors.toList());
    }

    public AnomalyScore calculateAnomalyScore(UUID userId, TransactionAnalytics transaction) {
        log.debug("Calculating anomaly score for transaction: {} user: {}", 
                transaction.getTransactionId(), userId);

        // Get user's historical patterns
        UserSpendingProfile profile = getUserSpendingProfile(userId);
        
        // Calculate various anomaly indicators
        BigDecimal amountScore = calculateAmountAnomalyScore(transaction.getAmount(), profile);
        BigDecimal timingScore = calculateTimingAnomalyScore(transaction.getTimestamp(), profile);
        BigDecimal categoryScore = calculateCategoryAnomalyScore(transaction.getCategory(), profile);
        BigDecimal merchantScore = calculateMerchantAnomalyScore(transaction.getMerchantId(), profile);
        BigDecimal frequencyScore = calculateFrequencyAnomalyScore(userId, transaction.getTimestamp());

        // Weighted composite score
        BigDecimal compositeScore = amountScore.multiply(BigDecimal.valueOf(0.3))
                .add(timingScore.multiply(BigDecimal.valueOf(0.2)))
                .add(categoryScore.multiply(BigDecimal.valueOf(0.2)))
                .add(merchantScore.multiply(BigDecimal.valueOf(0.15)))
                .add(frequencyScore.multiply(BigDecimal.valueOf(0.15)));

        AnomalyLevel level = determineAnomalyLevel(compositeScore);
        List<String> indicators = identifyAnomalyIndicators(amountScore, timingScore, categoryScore, merchantScore, frequencyScore);

        return AnomalyScore.builder()
                .overallScore(compositeScore)
                .level(level)
                .amountScore(amountScore)
                .timingScore(timingScore)
                .categoryScore(categoryScore)
                .merchantScore(merchantScore)
                .frequencyScore(frequencyScore)
                .indicators(indicators)
                .riskFactors(generateRiskFactors(transaction, compositeScore))
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    public BudgetRecommendation generateSmartBudgetRecommendation(UUID userId, 
                                                               SpendingForecast spendingForecast, 
                                                               IncomeForecast incomeForecast) {
        log.info("Generating smart budget recommendation for user: {}", userId);

        // Get current spending patterns
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(30);
        
        List<CategorySpending> currentSpending = analyticsRepository.getCategorySpending(userId, startDate, endDate);
        
        // Calculate recommended budget allocations
        Map<String, BigDecimal> recommendedAllocations = calculateOptimalAllocations(
                currentSpending, spendingForecast, incomeForecast);
        
        // Generate category-specific recommendations
        List<CategoryBudgetRecommendation> categoryRecommendations = currentSpending.stream()
                .map(cs -> generateCategoryBudgetRecommendation(cs, recommendedAllocations.get(cs.getCategory())))
                .collect(Collectors.toList());
        
        // Calculate savings potential
        BigDecimal savingsPotential = calculateSavingsPotential(incomeForecast.getTotalPredictedIncome(), 
                spendingForecast.getTotalPredictedSpending());
        
        // Generate actionable insights
        List<BudgetInsight> insights = generateBudgetInsights(currentSpending, recommendedAllocations);

        return BudgetRecommendation.builder()
                .userId(userId)
                .recommendedMonthlyBudget(recommendedAllocations.values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .categoryAllocations(recommendedAllocations)
                .categoryRecommendations(categoryRecommendations)
                .savingsPotential(savingsPotential)
                .emergencyFundRecommendation(calculateEmergencyFundRecommendation(incomeForecast))
                .insights(insights)
                .confidenceLevel(calculateBudgetConfidence(spendingForecast, incomeForecast))
                .reviewDate(LocalDateTime.now().plusDays(30))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // Helper Methods

    private List<SpendingPrediction> generateSpendingPredictions(List<DailySpending> historicalData, int daysAhead) {
        List<SpendingPrediction> predictions = new ArrayList<>();
        
        // Calculate moving averages and trends
        List<BigDecimal> amounts = historicalData.stream()
                .map(DailySpending::getAmount)
                .collect(Collectors.toList());
        
        // Simple moving average with trend adjustment
        BigDecimal recentAverage = calculateRecentAverage(amounts, 14); // 2-week average
        BigDecimal trend = calculateTrend(amounts);
        
        LocalDateTime startDate = LocalDateTime.now().plusDays(1);
        
        for (int i = 0; i < daysAhead; i++) {
            LocalDateTime predictDate = startDate.plusDays(i);
            
            // Apply day-of-week seasonality
            BigDecimal seasonalFactor = getDayOfWeekSeasonality(predictDate.getDayOfWeek(), historicalData);
            
            // Apply trend with diminishing effect over time
            BigDecimal trendAdjustment = trend.multiply(BigDecimal.valueOf(i))
                    .multiply(BigDecimal.valueOf(0.1)); // Trend dampening factor
            
            BigDecimal predictedAmount = recentAverage
                    .multiply(seasonalFactor)
                    .add(trendAdjustment)
                    .max(BigDecimal.ZERO); // Ensure non-negative
            
            // Calculate confidence interval
            BigDecimal variance = calculateVariance(amounts);
            BigDecimal confidenceInterval = variance.multiply(BigDecimal.valueOf(1.96)); // 95% CI
            
            predictions.add(SpendingPrediction.builder()
                    .date(predictDate.toLocalDate())
                    .predictedAmount(predictedAmount)
                    .lowerBound(predictedAmount.subtract(confidenceInterval).max(BigDecimal.ZERO))
                    .upperBound(predictedAmount.add(confidenceInterval))
                    .confidence(calculateDayConfidence(i, variance))
                    .build());
        }
        
        return predictions;
    }

    private BigDecimal calculateRecentAverage(List<BigDecimal> amounts, int days) {
        int startIndex = Math.max(0, amounts.size() - days);
        List<BigDecimal> recentAmounts = amounts.subList(startIndex, amounts.size());
        
        return recentAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recentAmounts.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTrend(List<BigDecimal> amounts) {
        if (amounts.size() < 2) return BigDecimal.ZERO;
        
        // Simple linear regression slope
        int n = amounts.size();
        BigDecimal sumX = BigDecimal.valueOf(n * (n + 1) / 2);
        BigDecimal sumY = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal sumXY = BigDecimal.ZERO;
        BigDecimal sumXX = BigDecimal.ZERO;
        
        for (int i = 0; i < n; i++) {
            BigDecimal x = BigDecimal.valueOf(i + 1);
            BigDecimal y = amounts.get(i);
            sumXY = sumXY.add(x.multiply(y));
            sumXX = sumXX.add(x.multiply(x));
        }
        
        BigDecimal slope = sumXY.multiply(BigDecimal.valueOf(n))
                .subtract(sumX.multiply(sumY))
                .divide(sumXX.multiply(BigDecimal.valueOf(n))
                        .subtract(sumX.multiply(sumX)), 6, RoundingMode.HALF_UP);
        
        return slope;
    }

    private BigDecimal getDayOfWeekSeasonality(DayOfWeek dayOfWeek, List<DailySpending> historicalData) {
        Map<DayOfWeek, List<BigDecimal>> dayGroups = historicalData.stream()
                .collect(Collectors.groupingBy(
                        ds -> ds.getDate().getDayOfWeek(),
                        Collectors.mapping(DailySpending::getAmount, Collectors.toList())
                ));
        
        BigDecimal overallAverage = historicalData.stream()
                .map(DailySpending::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(historicalData.size()), 4, RoundingMode.HALF_UP);
        
        List<BigDecimal> dayAmounts = dayGroups.get(dayOfWeek);
        if (dayAmounts == null || dayAmounts.isEmpty()) {
            return BigDecimal.ONE; // Default multiplier
        }
        
        BigDecimal dayAverage = dayAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dayAmounts.size()), 4, RoundingMode.HALF_UP);
        
        return overallAverage.compareTo(BigDecimal.ZERO) > 0 ?
                dayAverage.divide(overallAverage, 4, RoundingMode.HALF_UP) :
                BigDecimal.ONE;
    }

    private UserSpendingProfile getUserSpendingProfile(UUID userId) {
        String cacheKey = cacheService.buildUserKey(userId.toString(), "spending-profile");
        UserSpendingProfile cached = cacheService.get(cacheKey, UserSpendingProfile.class);
        
        if (cached != null) {
            return cached;
        }
        
        // Generate spending profile from historical data
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(90);
        
        List<TransactionAnalytics> transactions = analyticsRepository
                .findByUserIdAndTimestampBetween(userId, startDate, endDate);
        
        UserSpendingProfile profile = UserSpendingProfile.builder()
                .userId(userId)
                .averageTransactionAmount(calculateAverageAmount(transactions))
                .typicalSpendingHours(calculateTypicalHours(transactions))
                .frequentCategories(calculateFrequentCategories(transactions))
                .frequentMerchants(calculateFrequentMerchants(transactions))
                .dailySpendingPattern(calculateDailyPattern(transactions))
                .weeklySpendingPattern(calculateWeeklyPattern(transactions))
                .lastUpdated(LocalDateTime.now())
                .build();
        
        cacheService.set(cacheKey, profile, Duration.ofHours(6));
        return profile;
    }

    private SpendingForecast createDefaultSpendingForecast(UUID userId, int daysAhead) {
        // Create a basic forecast with minimal data
        return SpendingForecast.builder()
                .userId(userId)
                .forecastPeriodDays(daysAhead)
                .predictions(Collections.emptyList())
                .totalPredictedSpending(BigDecimal.ZERO)
                .averageDailySpending(BigDecimal.ZERO)
                .trend(SpendingTrend.STABLE)
                .seasonalFactors(Collections.emptyList())
                .confidenceLevel(BigDecimal.valueOf(0.3)) // Low confidence
                .riskFactors(List.of(RiskFactor.builder()
                        .factor("INSUFFICIENT_DATA")
                        .description("Not enough historical data for accurate prediction")
                        .severity("LOW")
                        .build()))
                .modelAccuracy(BigDecimal.valueOf(0.5))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // Additional helper methods would be implemented here...
    // This service provides ML-powered predictive analytics and anomaly detection
}