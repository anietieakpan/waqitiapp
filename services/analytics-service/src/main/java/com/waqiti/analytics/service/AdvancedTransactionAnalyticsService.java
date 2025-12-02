package com.waqiti.analytics.service;

import com.waqiti.analytics.domain.*;
import com.waqiti.analytics.dto.request.*;
import com.waqiti.analytics.dto.response.*;
import com.waqiti.analytics.dto.model.*;
import com.waqiti.analytics.repository.*;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedTransactionAnalyticsService {

    private final TransactionAnalyticsRepository analyticsRepository;
    private final TransactionRepository transactionRepository;
    private final SpendingPatternRepository spendingPatternRepository;
    private final FraudDetectionRepository fraudDetectionRepository;
    private final PredictiveAnalyticsRepository predictiveRepository;
    private final BehaviorAnalyticsRepository behaviorRepository;
    private final CacheService cacheService;
    private final EventPublisher eventPublisher;
    private final MLModelService mlModelService;
    private final AnomalyDetectionService anomalyDetectionService;

    @Transactional(readOnly = true)
    public ComprehensiveAnalyticsResponse getComprehensiveAnalytics(UUID userId, AnalyticsRequest request) {
        log.info("Generating comprehensive analytics for user: {} for period: {} to {}", 
                userId, request.getStartDate(), request.getEndDate());

        String cacheKey = cacheService.buildUserKey(userId.toString(), 
                "comprehensive-analytics", request.getStartDate().toString(), request.getEndDate().toString());
        
        ComprehensiveAnalyticsResponse cached = cacheService.get(cacheKey, ComprehensiveAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        ComprehensiveAnalyticsResponse response = ComprehensiveAnalyticsResponse.builder()
                .userId(userId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .transactionSummary(generateTransactionSummary(userId, request))
                .spendingAnalysis(generateSpendingAnalysis(userId, request))
                .incomeAnalysis(generateIncomeAnalysis(userId, request))
                .categorySpending(generateCategorySpending(userId, request))
                .dailySpending(generateDailySpending(userId, request))
                .hourlySpending(generateHourlySpending(userId, request))
                .spendingPatterns(generateSpendingPatterns(userId, request))
                .behaviorInsights(generateBehaviorInsights(userId, request))
                .forecasts(generateForecasts(userId, request))
                .anomalies(generateAnomalies(userId, request))
                .riskAssessment(generateRiskAssessment(userId, request))
                .generatedAt(LocalDateTime.now())
                .build();

        // Cache for 1 hour
        cacheService.set(cacheKey, response, Duration.ofHours(1));
        
        return response;
    }

    private TransactionSummary generateTransactionSummary(UUID userId, AnalyticsRequest request) {
        List<Object[]> summaryData = analyticsRepository.getTransactionSummary(
                userId, request.getStartDate(), request.getEndDate());

        if (summaryData.isEmpty()) {
            return TransactionSummary.builder()
                    .totalTransactions(0L)
                    .totalSpent(BigDecimal.ZERO)
                    .totalReceived(BigDecimal.ZERO)
                    .netCashFlow(BigDecimal.ZERO)
                    .averageTransactionAmount(BigDecimal.ZERO)
                    .largestTransaction(BigDecimal.ZERO)
                    .smallestTransaction(BigDecimal.ZERO)
                    .build();
        }

        Object[] data = summaryData.get(0);
        
        return TransactionSummary.builder()
                .totalTransactions(((Number) data[0]).longValue())
                .totalSpent((BigDecimal) data[1])
                .totalReceived((BigDecimal) data[2])
                .netCashFlow(((BigDecimal) data[2]).subtract((BigDecimal) data[1]))
                .averageTransactionAmount((BigDecimal) data[3])
                .largestTransaction((BigDecimal) data[4])
                .smallestTransaction((BigDecimal) data[5])
                .uniqueMerchants(((Number) data[6]).intValue())
                .uniqueCategories(((Number) data[7]).intValue())
                .build();
    }

    private SpendingAnalysis generateSpendingAnalysis(UUID userId, AnalyticsRequest request) {
        List<CategorySpending> categorySpending = mapToCategorySpending(
                analyticsRepository.getCategorySpending(userId, request.getStartDate(), request.getEndDate()));

        List<DailySpending> dailySpending = mapToDailySpending(
                analyticsRepository.getDailySpending(userId, request.getStartDate(), request.getEndDate()));

        List<HourlySpending> hourlySpending = mapToHourlySpending(
                analyticsRepository.getHourlySpending(userId, request.getStartDate(), request.getEndDate()));

        BigDecimal totalSpent = categorySpending.stream()
                .map(CategorySpending::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate spending velocity (transactions per day)
        long daysBetween = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
        BigDecimal spendingVelocity = daysBetween > 0 ? 
                totalSpent.divide(BigDecimal.valueOf(daysBetween), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO;

        // Detect spending trends
        SpendingTrend trend = calculateSpendingTrend(dailySpending);

        return SpendingAnalysis.builder()
                .totalSpent(totalSpent)
                .categoryBreakdown(categorySpending)
                .dailySpending(dailySpending)
                .hourlySpending(hourlySpending)
                .spendingVelocity(spendingVelocity)
                .spendingTrend(trend)
                .averageDailySpend(spendingVelocity)
                .peakSpendingHour(findPeakSpendingHour(hourlySpending))
                .spendingConsistency(calculateSpendingConsistency(dailySpending))
                .build();
    }

    private IncomeAnalysis generateIncomeAnalysis(UUID userId, AnalyticsRequest request) {
        List<IncomeSource> incomeSources = mapToIncomeSources(
                analyticsRepository.getIncomeSources(userId, request.getStartDate(), request.getEndDate()));

        List<DailyIncome> dailyIncome = mapToDailyIncome(
                analyticsRepository.getDailyIncome(userId, request.getStartDate(), request.getEndDate()));

        BigDecimal totalIncome = incomeSources.stream()
                .map(IncomeSource::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        IncomeStability stability = calculateIncomeStability(dailyIncome);

        return IncomeAnalysis.builder()
                .totalIncome(totalIncome)
                .incomeSources(incomeSources)
                .dailyIncome(dailyIncome)
                .averageDailyIncome(calculateAverageDailyIncome(dailyIncome))
                .incomeStability(stability)
                .incomeGrowthRate(calculateIncomeGrowthRate(userId, request))
                .primaryIncomeSource(findPrimaryIncomeSource(incomeSources))
                .incomeConsistency(calculateIncomeConsistency(dailyIncome))
                .build();
    }

    private CashFlowAnalysis generateCashFlowAnalysis(UUID userId, AnalyticsRequest request) {
        List<CashFlowData> cashFlowData = mapToCashFlowData(
                analyticsRepository.getCashFlowData(userId, request.getStartDate(), request.getEndDate()));

        BigDecimal netCashFlow = cashFlowData.stream()
                .map(CashFlowData::getNetFlow)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CashFlowTrend trend = analyzeCashFlowTrend(cashFlowData);
        
        List<CashFlowForecast> forecast = generateCashFlowForecast(userId, cashFlowData);

        return CashFlowAnalysis.builder()
                .netCashFlow(netCashFlow)
                .cashFlowData(cashFlowData)
                .cashFlowTrend(trend)
                .averageWeeklyCashFlow(calculateAverageWeeklyCashFlow(cashFlowData))
                .cashFlowVolatility(calculateCashFlowVolatility(cashFlowData))
                .positiveFlowDays(countPositiveFlowDays(cashFlowData))
                .negativeFlowDays(countNegativeFlowDays(cashFlowData))
                .forecast(forecast)
                .build();
    }

    private List<CategoryInsight> generateCategoryInsights(UUID userId, AnalyticsRequest request) {
        List<CategorySpending> categorySpending = analyticsRepository.getCategorySpending(
                userId, request.getStartDate(), request.getEndDate());

        // Get previous period for comparison
        LocalDateTime previousStart = request.getStartDate().minus(
                Duration.between(request.getStartDate(), request.getEndDate()));
        List<CategorySpending> previousSpending = analyticsRepository.getCategorySpending(
                userId, previousStart, request.getStartDate());

        Map<String, BigDecimal> previousSpendingMap = previousSpending.stream()
                .collect(Collectors.toMap(
                        CategorySpending::getCategory, 
                        CategorySpending::getAmount,
                        (a, b) -> a));

        BigDecimal totalSpent = categorySpending.stream()
                .map(CategorySpending::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return categorySpending.stream()
                .map(current -> {
                    BigDecimal previousAmount = previousSpendingMap.getOrDefault(
                            current.getCategory(), BigDecimal.ZERO);
                    
                    BigDecimal changeAmount = current.getAmount().subtract(previousAmount);
                    BigDecimal changePercentage = previousAmount.compareTo(BigDecimal.ZERO) > 0 ?
                            changeAmount.divide(previousAmount, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100)) :
                            BigDecimal.ZERO;

                    BigDecimal percentage = totalSpent.compareTo(BigDecimal.ZERO) > 0 ?
                            current.getAmount().divide(totalSpent, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100)) :
                            BigDecimal.ZERO;

                    CategoryBehavior behavior = analyzeCategoryBehavior(userId, current.getCategory(), request);

                    return CategoryInsight.builder()
                            .category(current.getCategory())
                            .amount(current.getAmount())
                            .percentage(percentage)
                            .transactionCount(current.getTransactionCount())
                            .averageAmount(current.getAmount().divide(
                                    BigDecimal.valueOf(Math.max(1, current.getTransactionCount())), 
                                    2, RoundingMode.HALF_UP))
                            .changeFromPrevious(changeAmount)
                            .changePercentage(changePercentage)
                            .trend(changePercentage.compareTo(BigDecimal.ZERO) > 0 ? "INCREASING" : 
                                   changePercentage.compareTo(BigDecimal.ZERO) < 0 ? "DECREASING" : "STABLE")
                            .behavior(behavior)
                            .insights(generateCategorySpecificInsights(current, behavior))
                            .build();
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .collect(Collectors.toList());
    }

    private MerchantAnalysis generateMerchantAnalysis(UUID userId, AnalyticsRequest request) {
        List<MerchantSpending> topMerchants = mapToMerchantSpending(
                analyticsRepository.getTopMerchants(userId, request.getStartDate(), request.getEndDate(), 10));

        List<MerchantFrequency> frequentMerchants = mapToMerchantFrequency(
                analyticsRepository.getMerchantFrequency(userId, request.getStartDate(), request.getEndDate()));

        List<MerchantLoyalty> loyaltyAnalysis = analyzeMerchantLoyalty(userId, request);

        return MerchantAnalysis.builder()
                .topMerchantsBySpending(topMerchants)
                .topMerchantsByFrequency(frequentMerchants)
                .loyaltyAnalysis(loyaltyAnalysis)
                .averageSpendPerMerchant(calculateAverageSpendPerMerchant(topMerchants))
                .merchantDiversity(calculateMerchantDiversity(topMerchants))
                .newMerchantsThisPeriod(findNewMerchants(userId, request))
                .build();
    }

    private BehavioralPatterns generateBehavioralPatterns(UUID userId, AnalyticsRequest request) {
        SpendingPattern spendingPattern = analyzeSpendingPattern(userId, request);
        TimingPattern timingPattern = analyzeTimingPattern(userId, request);
        LocationPattern locationPattern = analyzeLocationPattern(userId, request);
        DigitalPattern digitalPattern = analyzeDigitalPattern(userId, request);

        return BehavioralPatterns.builder()
                .spendingPattern(spendingPattern)
                .timingPattern(timingPattern)
                .locationPattern(locationPattern)
                .digitalPattern(digitalPattern)
                .riskProfile(calculateRiskProfile(userId, request))
                .impulseBuyingTendency(calculateImpulseBuyingTendency(userId, request))
                .budgetAdherence(calculateBudgetAdherence(userId, request))
                .build();
    }

    private FraudRiskAssessment generateFraudRiskAssessment(UUID userId, AnalyticsRequest request) {
        List<TransactionAnomaly> anomalies = anomalyDetectionService
                .detectAnomalies(userId, request.getStartDate(), request.getEndDate());

        FraudRiskScore riskScore = calculateFraudRiskScore(userId, anomalies);
        
        List<SecurityRecommendation> recommendations = generateSecurityRecommendations(riskScore, anomalies);

        return FraudRiskAssessment.builder()
                .overallRiskScore(riskScore.getOverallScore())
                .riskLevel(riskScore.getRiskLevel())
                .riskFactors(riskScore.getRiskFactors())
                .anomalies(anomalies)
                .unusualPatterns(findUnusualPatterns(userId, request))
                .securityRecommendations(recommendations)
                .lastSecurityReview(getLastSecurityReview(userId))
                .complianceStatus(checkComplianceStatus(userId))
                .build();
    }

    private FinancialHealthScore calculateFinancialHealthScore(UUID userId, AnalyticsRequest request) {
        SpendingBehaviorScore spendingScore = calculateSpendingBehaviorScore(userId, request);
        SavingsRateScore savingsScore = calculateSavingsRateScore(userId, request);
        DebtToIncomeScore debtScore = calculateDebtToIncomeScore(userId, request);
        TransactionPatternScore patternScore = calculateTransactionPatternScore(userId, request);

        BigDecimal overallScore = spendingScore.getScore()
                .add(savingsScore.getScore())
                .add(debtScore.getScore())
                .add(patternScore.getScore())
                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);

        String healthLevel = determineHealthLevel(overallScore);
        List<HealthImprovement> improvements = generateHealthImprovements(
                spendingScore, savingsScore, debtScore, patternScore);

        return FinancialHealthScore.builder()
                .overallScore(overallScore)
                .healthLevel(healthLevel)
                .spendingBehaviorScore(spendingScore)
                .savingsRateScore(savingsScore)
                .debtToIncomeScore(debtScore)
                .transactionPatternScore(patternScore)
                .improvements(improvements)
                .lastCalculated(LocalDateTime.now())
                .build();
    }

    private PredictiveInsights generatePredictiveInsights(UUID userId, AnalyticsRequest request) {
        SpendingForecast spendingForecast = mlModelService.predictFutureSpending(userId, 30);
        IncomeForecast incomeForecast = mlModelService.predictFutureIncome(userId, 30);
        
        List<CategoryTrend> categoryTrends = predictCategoryTrends(userId, request);
        List<SeasonalPattern> seasonalPatterns = identifySeasonalPatterns(userId);
        
        BudgetRecommendation budgetRecommendation = generateBudgetRecommendation(
                userId, spendingForecast, incomeForecast);

        return PredictiveInsights.builder()
                .spendingForecast(spendingForecast)
                .incomeForecast(incomeForecast)
                .categoryTrends(categoryTrends)
                .seasonalPatterns(seasonalPatterns)
                .budgetRecommendation(budgetRecommendation)
                .confidenceLevel(calculatePredictionConfidence(userId))
                .riskFactors(identifyFutureRiskFactors(userId, spendingForecast))
                .opportunities(identifyOptimizationOpportunities(userId, request))
                .build();
    }

    private List<PersonalizedRecommendation> generatePersonalizedRecommendations(UUID userId, AnalyticsRequest request) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();

        // Spending optimization recommendations
        recommendations.addAll(generateSpendingOptimizationRecommendations(userId, request));
        
        // Savings recommendations
        recommendations.addAll(generateSavingsRecommendations(userId, request));
        
        // Investment recommendations
        recommendations.addAll(generateInvestmentRecommendations(userId, request));
        
        // Security recommendations
        recommendations.addAll(generateSecurityRecommendations(userId, request));
        
        // Budgeting recommendations
        recommendations.addAll(generateBudgetingRecommendations(userId, request));

        return recommendations.stream()
                .sorted((a, b) -> b.getPriority().compareTo(a.getPriority()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private BenchmarkingAnalysis generateBenchmarkingAnalysis(UUID userId, AnalyticsRequest request) {
        UserProfile userProfile = getUserProfile(userId);
        
        PeerComparison peerComparison = compareToPeers(userId, userProfile, request);
        IndustryComparison industryComparison = compareToIndustryAverages(userId, userProfile, request);
        DemographicComparison demographicComparison = compareToDemographics(userId, userProfile, request);

        return BenchmarkingAnalysis.builder()
                .peerComparison(peerComparison)
                .industryComparison(industryComparison)
                .demographicComparison(demographicComparison)
                .percentileRanking(calculatePercentileRanking(userId, userProfile, request))
                .competitiveAdvantages(identifyCompetitiveAdvantages(userId, peerComparison))
                .improvementAreas(identifyImprovementAreas(userId, peerComparison))
                .build();
    }

    // Helper methods implementation
    private SpendingTrend calculateSpendingTrend(List<DailySpending> dailySpending) {
        if (dailySpending.size() < 2) {
            return SpendingTrend.STABLE;
        }

        BigDecimal firstHalf = dailySpending.subList(0, dailySpending.size() / 2).stream()
                .map(DailySpending::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal secondHalf = dailySpending.subList(dailySpending.size() / 2, dailySpending.size()).stream()
                .map(DailySpending::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal changePercentage = firstHalf.compareTo(BigDecimal.ZERO) > 0 ?
                secondHalf.subtract(firstHalf).divide(firstHalf, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;

        if (changePercentage.compareTo(BigDecimal.valueOf(10)) > 0) {
            return SpendingTrend.INCREASING;
        } else if (changePercentage.compareTo(BigDecimal.valueOf(-10)) < 0) {
            return SpendingTrend.DECREASING;
        } else {
            return SpendingTrend.STABLE;
        }
    }

    private Integer findPeakSpendingHour(List<HourlySpending> hourlySpending) {
        return hourlySpending.stream()
                .max((a, b) -> a.getAmount().compareTo(b.getAmount()))
                .map(HourlySpending::getHour)
                .orElse(12); // Default to noon
    }

    private BigDecimal calculateSpendingConsistency(List<DailySpending> dailySpending) {
        if (dailySpending.isEmpty()) return BigDecimal.ZERO;

        BigDecimal average = dailySpending.stream()
                .map(DailySpending::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailySpending.size()), 2, RoundingMode.HALF_UP);

        BigDecimal sumSquaredDiffs = dailySpending.stream()
                .map(ds -> ds.getAmount().subtract(average).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal variance = sumSquaredDiffs.divide(BigDecimal.valueOf(dailySpending.size()), 2, RoundingMode.HALF_UP);
        
        // Return consistency as inverse of coefficient of variation (1 - CV)
        if (average.compareTo(BigDecimal.ZERO) > 0) {
            // Calculate standard deviation using Newton's method for square root
            BigDecimal standardDeviation = sqrt(variance, new MathContext(10));
            BigDecimal coefficientOfVariation = standardDeviation.divide(average, 4, RoundingMode.HALF_UP);
            return BigDecimal.ONE.subtract(coefficientOfVariation);
        }
        return BigDecimal.ZERO;
    }

    // Newton's method for BigDecimal square root
    private BigDecimal sqrt(BigDecimal value, MathContext mc) {
        if (value.equals(BigDecimal.ZERO)) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal x = value;
        BigDecimal previous;
        do {
            previous = x;
            x = x.add(value.divide(x, mc)).divide(BigDecimal.valueOf(2), mc);
        } while (!x.equals(previous));
        
        return x;
    }

    // Missing helper method implementations
    private IncomeStability calculateIncomeStability(List<DailyIncome> dailyIncome) {
        if (dailyIncome.isEmpty() || dailyIncome.size() < 7) {
            return IncomeStability.STABLE;
        }

        BigDecimal average = dailyIncome.stream()
                .map(DailyIncome::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyIncome.size()), 2, RoundingMode.HALF_UP);

        BigDecimal variance = dailyIncome.stream()
                .map(di -> di.getAmount().subtract(average).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyIncome.size()), 2, RoundingMode.HALF_UP);

        BigDecimal standardDeviation = sqrt(variance, new MathContext(10));
        BigDecimal coefficientOfVariation = average.compareTo(BigDecimal.ZERO) > 0 ?
                standardDeviation.divide(average, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        if (coefficientOfVariation.compareTo(BigDecimal.valueOf(0.3)) > 0) {
            return IncomeStability.VOLATILE;
        } else if (coefficientOfVariation.compareTo(BigDecimal.valueOf(0.1)) < 0) {
            return IncomeStability.STABLE;
        } else {
            // Check growth trend
            BigDecimal firstHalf = dailyIncome.subList(0, dailyIncome.size() / 2).stream()
                    .map(DailyIncome::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal secondHalf = dailyIncome.subList(dailyIncome.size() / 2, dailyIncome.size()).stream()
                    .map(DailyIncome::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return secondHalf.compareTo(firstHalf) > 0 ? IncomeStability.GROWING : IncomeStability.DECLINING;
        }
    }

    // Additional stub methods for compilation - these would need proper implementation
    private BigDecimal calculateAverageDailyIncome(List<DailyIncome> dailyIncome) {
        if (dailyIncome.isEmpty()) return BigDecimal.ZERO;
        return dailyIncome.stream()
                .map(DailyIncome::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyIncome.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateIncomeGrowthRate(UUID userId, AnalyticsRequest request) {
        // Stub implementation
        return BigDecimal.valueOf(5.2); // 5.2% growth
    }

    private IncomeSource findPrimaryIncomeSource(List<IncomeSource> incomeSources) {
        return incomeSources.stream()
                .max(Comparator.comparing(IncomeSource::getAmount))
                .orElse(null);
    }

    private BigDecimal calculateIncomeConsistency(List<DailyIncome> dailyIncome) {
        // Reuse the spending consistency calculation logic
        return calculateSpendingConsistency(dailyIncome.stream()
                .map(di -> new DailySpending() {
                    @Override
                    public BigDecimal getAmount() { return di.getAmount(); }
                    @Override
                    public LocalDateTime getDate() { return di.getDate(); }
                    @Override
                    public Integer getTransactionCount() { return di.getTransactionCount(); }
                })
                .collect(Collectors.toList()));
    }

    // This service provides comprehensive transaction analytics with ML-powered insights

    @Transactional
    public void processTransactionForAnalytics(UUID transactionId, UUID userId, 
                                             BigDecimal amount, String category, 
                                             String merchantId, LocalDateTime timestamp) {
        log.info("Processing transaction for analytics: {} for user: {}", transactionId, userId);

        // Store transaction data for analytics
        TransactionAnalytics analytics = TransactionAnalytics.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .category(category)
                .merchantId(merchantId)
                .timestamp(timestamp)
                .build();

        analyticsRepository.save(analytics);

        // Trigger real-time anomaly detection
        anomalyDetectionService.checkForAnomalies(userId, analytics);

        // Update behavioral patterns
        behaviorRepository.updateBehavioralPatterns(userId, analytics);

        // Clear relevant caches
        cacheService.evictUserPattern(userId.toString(), "comprehensive-analytics*");
        
        log.info("Transaction analytics processed for transaction: {}", transactionId);
    }
    
    // Additional stub implementations for missing methods
    
    private List<CategorySpending> generateCategorySpending(UUID userId, AnalyticsRequest request) {
        log.debug("Generating category spending for user: {}", userId);
        
        // Fetch transactions from database and group by category
        List<Object[]> categoryData = transactionRepository.getCategorySpendingByUser(
                userId, request.getStartDate(), request.getEndDate());
        
        List<CategorySpending> categorySpending = new ArrayList<>();
        BigDecimal totalSpending = calculateTotalSpending(userId, request.getStartDate(), request.getEndDate());
        
        for (Object[] row : categoryData) {
            String categoryName = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            Long transactionCount = (Long) row[2];
            
            BigDecimal percentage = totalSpending.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(totalSpending, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            
            BigDecimal averagePerTransaction = transactionCount > 0
                    ? amount.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            // Calculate month-over-month change
            BigDecimal previousMonthAmount = getPreviousMonthCategorySpending(userId, categoryName, request.getStartDate());
            BigDecimal monthOverMonthChange = calculatePercentageChange(previousMonthAmount, amount);
            
            categorySpending.add(CategorySpending.builder()
                    .categoryName(categoryName)
                    .amount(amount)
                    .transactionCount(transactionCount)
                    .percentage(percentage)
                    .averagePerTransaction(averagePerTransaction)
                    .monthOverMonthChange(monthOverMonthChange)
                    .build());
        }
        
        // Sort by amount descending
        categorySpending.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));
        
        return categorySpending;
    }
    
    private List<DailySpending> generateDailySpending(UUID userId, AnalyticsRequest request) {
        log.debug("Generating daily spending for user: {}", userId);
        
        // Get daily spending data from repository
        List<Object[]> dailyData = transactionRepository.getDailySpending(
                userId, request.getStartDate(), request.getEndDate());
        
        List<DailySpending> dailySpending = new ArrayList<>();
        
        for (Object[] row : dailyData) {
            LocalDate date = (LocalDate) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            Long transactionCount = (Long) row[2];
            
            BigDecimal averagePerTransaction = transactionCount > 0
                    ? amount.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            dailySpending.add(DailySpending.builder()
                    .date(date.toString())
                    .amount(amount)
                    .transactionCount(transactionCount)
                    .averagePerTransaction(averagePerTransaction)
                    .build());
        }
        
        // Fill in missing dates with zero values
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();
        Map<String, DailySpending> spendingMap = dailySpending.stream()
                .collect(Collectors.toMap(DailySpending::getDate, Function.identity()));
        
        List<DailySpending> completeDailySpending = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String dateStr = date.toString();
            if (spendingMap.containsKey(dateStr)) {
                completeDailySpending.add(spendingMap.get(dateStr));
            } else {
                completeDailySpending.add(DailySpending.builder()
                        .date(dateStr)
                        .amount(BigDecimal.ZERO)
                        .transactionCount(0L)
                        .averagePerTransaction(BigDecimal.ZERO)
                        .build());
            }
        }
        
        return completeDailySpending;
    }
    
    private List<HourlySpending> generateHourlySpending(UUID userId, AnalyticsRequest request) {
        log.debug("Generating hourly spending analysis for user: {}", userId);
        
        // Get hourly spending data from repository
        List<Object[]> hourlyData = analyticsRepository.getHourlySpendingData(
                userId, request.getStartDate(), request.getEndDate());
        
        Map<Integer, HourlySpending> hourlyMap = new HashMap<>();
        
        // Initialize all hours with zero values
        for (int hour = 0; hour < 24; hour++) {
            hourlyMap.put(hour, HourlySpending.builder()
                    .hour(hour)
                    .amount(BigDecimal.ZERO)
                    .transactionCount(0L)
                    .averagePerTransaction(BigDecimal.ZERO)
                    .peakDay("")
                    .build());
        }
        
        // Populate with actual data
        for (Object[] data : hourlyData) {
            Integer hour = ((Number) data[0]).intValue();
            BigDecimal totalAmount = (BigDecimal) data[1];
            Long transactionCount = ((Number) data[2]).longValue();
            
            BigDecimal avgPerTransaction = transactionCount > 0 
                    ? totalAmount.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            // Find peak day for this hour
            String peakDay = analyticsRepository.getPeakDayForHour(
                    userId, hour, request.getStartDate(), request.getEndDate());
            
            hourlyMap.put(hour, HourlySpending.builder()
                    .hour(hour)
                    .amount(totalAmount)
                    .transactionCount(transactionCount)
                    .averagePerTransaction(avgPerTransaction)
                    .peakDay(peakDay != null ? peakDay : "")
                    .percentageOfTotal(BigDecimal.ZERO) // Will calculate after
                    .build());
        }
        
        // Calculate percentage of total for each hour
        BigDecimal totalDaySpending = hourlyMap.values().stream()
                .map(HourlySpending::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalDaySpending.compareTo(BigDecimal.ZERO) > 0) {
            hourlyMap.values().forEach(hs -> 
                hs.setPercentageOfTotal(
                    hs.getAmount().multiply(BigDecimal.valueOf(100))
                        .divide(totalDaySpending, 2, RoundingMode.HALF_UP)
                )
            );
        }
        
        // Sort by hour and return as list
        return hourlyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
    
    private List<SpendingPattern> generateSpendingPatterns(UUID userId, AnalyticsRequest request) {
        log.debug("Generating spending patterns for user: {}", userId);
        
        // Analyze transaction patterns using time-series analysis
        List<Transaction> transactions = transactionRepository.findByUserIdAndDateBetween(
                userId, request.getStartDate(), request.getEndDate());
        
        List<SpendingPattern> patterns = new ArrayList<>();
        
        // Weekend vs Weekday Pattern Analysis
        Map<String, List<Transaction>> weekdayGroups = transactions.stream()
                .collect(Collectors.groupingBy(t -> 
                    t.getTransactionDate().getDayOfWeek().getValue() >= 6 ? "WEEKEND" : "WEEKDAY"));
        
        for (Map.Entry<String, List<Transaction>> entry : weekdayGroups.entrySet()) {
            if (entry.getValue().size() >= 5) {
                BigDecimal avgAmount = entry.getValue().stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(entry.getValue().size()), 2, RoundingMode.HALF_UP);
                
                Map<String, Long> categoryFreq = entry.getValue().stream()
                        .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.counting()));
                
                patterns.add(SpendingPattern.builder()
                        .patternName(entry.getKey() + " Spending Pattern")
                        .description("Typical spending behavior on " + entry.getKey().toLowerCase())
                        .frequency(BigDecimal.valueOf(entry.getValue().size())
                                .divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP))
                        .averageAmount(avgAmount)
                        .categories(categoryFreq.entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .limit(3)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()))
                        .timePattern(entry.getKey())
                        .confidence(calculatePatternConfidence(entry.getValue()))
                        .build());
            }
        }
        
        // Time-of-day Pattern Analysis
        Map<String, List<Transaction>> timeGroups = transactions.stream()
                .collect(Collectors.groupingBy(t -> {
                    int hour = t.getTransactionDate().getHour();
                    if (hour < 6) return "EARLY_MORNING";
                    else if (hour < 12) return "MORNING";
                    else if (hour < 18) return "AFTERNOON";
                    else return "EVENING";
                }));
        
        for (Map.Entry<String, List<Transaction>> entry : timeGroups.entrySet()) {
            if (entry.getValue().size() >= 10) {
                patterns.add(createTimeBasedPattern(entry.getKey(), entry.getValue()));
            }
        }
        
        // Recurring Transaction Pattern Detection
        Map<String, List<Transaction>> merchantGroups = transactions.stream()
                .filter(t -> t.getMerchant() != null)
                .collect(Collectors.groupingBy(Transaction::getMerchant));
        
        for (Map.Entry<String, List<Transaction>> entry : merchantGroups.entrySet()) {
            if (entry.getValue().size() >= 3) {
                RecurringPattern recurring = detectRecurringPattern(entry.getValue());
                if (recurring != null) {
                    patterns.add(recurring.toSpendingPattern());
                }
            }
        }
        
        return patterns;
    }
    
    private BehaviorInsights generateBehaviorInsights(UUID userId, AnalyticsRequest request) {
        log.debug("Generating comprehensive behavior insights for user: {}", userId);
        
        try {
            // Get transaction data for analysis
            List<TransactionAnalytics> transactions = analyticsRepository
                    .findByUserIdAndTimestampBetween(userId, request.getStartDate(), request.getEndDate());
            
            if (transactions.isEmpty()) {
                return BehaviorInsights.builder()
                        .spendingPersonality("INSUFFICIENT_DATA")
                        .behaviorTrends(Arrays.asList("Not enough data for analysis"))
                        .riskIndicators(new HashMap<>())
                        .recommendations(Arrays.asList("Continue using the service to generate insights"))
                        .build();
            }
            
            // Analyze spending personality
            String spendingPersonality = analyzeSpendingPersonality(transactions);
            
            // Identify behavior trends
            List<String> behaviorTrends = identifyBehaviorTrends(userId, transactions, request);
            
            // Calculate risk indicators
            Map<String, Object> riskIndicators = calculateRiskIndicators(transactions);
            
            // Generate personalized recommendations
            List<String> recommendations = generatePersonalizedRecommendations(
                    spendingPersonality, behaviorTrends, riskIndicators, transactions);
            
            // Calculate behavior scores
            Map<String, BigDecimal> behaviorScores = calculateBehaviorScores(transactions);
            
            return BehaviorInsights.builder()
                    .spendingPersonality(spendingPersonality)
                    .behaviorTrends(behaviorTrends)
                    .riskIndicators(riskIndicators)
                    .recommendations(recommendations)
                    .behaviorScores(behaviorScores)
                    .insightGeneratedAt(LocalDateTime.now())
                    .confidenceLevel(calculateConfidenceLevel(transactions.size()))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating behavior insights for user: {}", userId, e);
            return BehaviorInsights.builder()
                    .spendingPersonality("ERROR")
                    .behaviorTrends(Arrays.asList("Error analyzing behavior"))
                    .riskIndicators(new HashMap<>())
                    .recommendations(Arrays.asList("Please try again later"))
                    .build();
        }
    }
    
    private String analyzeSpendingPersonality(List<TransactionAnalytics> transactions) {
        // Calculate spending metrics
        BigDecimal totalSpent = transactions.stream()
                .map(TransactionAnalytics::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal avgTransaction = totalSpent.divide(
                BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);
        
        // Calculate variance
        BigDecimal variance = calculateVariance(transactions, avgTransaction);
        
        // Analyze categories
        Map<String, Long> categoryFrequency = transactions.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(TransactionAnalytics::getCategory, Collectors.counting()));
        
        long essentialCount = categoryFrequency.entrySet().stream()
                .filter(e -> isEssentialCategory(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        
        double essentialRatio = (double) essentialCount / transactions.size();
        
        // Determine personality based on metrics
        if (variance.compareTo(BigDecimal.valueOf(0.3)) < 0 && essentialRatio > 0.7) {
            return "DISCIPLINED_SAVER";
        } else if (variance.compareTo(BigDecimal.valueOf(0.5)) < 0 && essentialRatio > 0.5) {
            return "BALANCED_SPENDER";
        } else if (variance.compareTo(BigDecimal.valueOf(0.7)) > 0 && essentialRatio < 0.4) {
            return "IMPULSE_BUYER";
        } else if (avgTransaction.compareTo(BigDecimal.valueOf(100)) > 0) {
            return "BIG_TICKET_BUYER";
        } else if (transactions.size() > 100) {
            return "FREQUENT_TRANSACTOR";
        } else {
            return "MODERATE_SPENDER";
        }
    }
    
    private List<String> identifyBehaviorTrends(UUID userId, List<TransactionAnalytics> transactions, 
                                               AnalyticsRequest request) {
        List<String> trends = new ArrayList<>();
        
        // Compare with previous period
        LocalDateTime previousStart = request.getStartDate().minusMonths(1);
        LocalDateTime previousEnd = request.getEndDate().minusMonths(1);
        
        List<TransactionAnalytics> previousTransactions = analyticsRepository
                .findByUserIdAndTimestampBetween(userId, previousStart, previousEnd);
        
        if (!previousTransactions.isEmpty()) {
            BigDecimal currentTotal = transactions.stream()
                    .map(TransactionAnalytics::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal previousTotal = previousTransactions.stream()
                    .map(TransactionAnalytics::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal changePercent = currentTotal.subtract(previousTotal)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(previousTotal.compareTo(BigDecimal.ZERO) > 0 ? previousTotal : BigDecimal.ONE, 
                            2, RoundingMode.HALF_UP);
            
            if (changePercent.compareTo(BigDecimal.valueOf(20)) > 0) {
                trends.add("Spending increased by " + changePercent + "% from last period");
            } else if (changePercent.compareTo(BigDecimal.valueOf(-20)) < 0) {
                trends.add("Spending decreased by " + changePercent.abs() + "% from last period");
            } else {
                trends.add("Spending remained stable compared to last period");
            }
            
            // Category shifts
            Map<String, Long> currentCategories = transactions.stream()
                    .filter(t -> t.getCategory() != null)
                    .collect(Collectors.groupingBy(TransactionAnalytics::getCategory, Collectors.counting()));
            
            Map<String, Long> previousCategories = previousTransactions.stream()
                    .filter(t -> t.getCategory() != null)
                    .collect(Collectors.groupingBy(TransactionAnalytics::getCategory, Collectors.counting()));
            
            for (Map.Entry<String, Long> entry : currentCategories.entrySet()) {
                Long previousCount = previousCategories.getOrDefault(entry.getKey(), 0L);
                if (entry.getValue() > previousCount * 1.5) {
                    trends.add("Increased spending in " + entry.getKey().toLowerCase());
                }
            }
        }
        
        // Time-based trends
        Map<String, Long> dayOfWeekFrequency = transactions.stream()
                .collect(Collectors.groupingBy(t -> 
                        t.getTimestamp().getDayOfWeek().toString(), Collectors.counting()));
        
        String peakDay = dayOfWeekFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        
        if (!peakDay.isEmpty()) {
            trends.add("Most active on " + peakDay.toLowerCase() + "s");
        }
        
        return trends;
    }
    
    private Map<String, Object> calculateRiskIndicators(List<TransactionAnalytics> transactions) {
        Map<String, Object> indicators = new HashMap<>();
        
        // Failed transaction rate
        long failedCount = transactions.stream()
                .filter(t -> "FAILED".equals(t.getStatus()))
                .count();
        
        double failureRate = (double) failedCount / transactions.size();
        indicators.put("failureRate", String.format("%.2f%%", failureRate * 100));
        
        // High-value transaction count
        long highValueCount = transactions.stream()
                .filter(t -> t.getAmount().compareTo(BigDecimal.valueOf(500)) > 0)
                .count();
        
        indicators.put("highValueTransactions", highValueCount);
        
        // Unusual time transactions
        long unusualTimeCount = transactions.stream()
                .filter(t -> {
                    int hour = t.getTimestamp().getHour();
                    return hour < 6 || hour > 23;
                })
                .count();
        
        indicators.put("offHoursTransactions", unusualTimeCount);
        
        // Velocity indicator
        if (transactions.size() >= 2) {
            long timeDiffHours = ChronoUnit.HOURS.between(
                    transactions.get(0).getTimestamp(),
                    transactions.get(transactions.size() - 1).getTimestamp());
            
            double transactionsPerHour = timeDiffHours > 0 
                    ? (double) transactions.size() / timeDiffHours : 0;
            
            indicators.put("transactionVelocity", String.format("%.2f/hour", transactionsPerHour));
        }
        
        return indicators;
    }
    
    private List<String> generatePersonalizedRecommendations(String personality, 
                                                            List<String> trends, 
                                                            Map<String, Object> riskIndicators,
                                                            List<TransactionAnalytics> transactions) {
        List<String> recommendations = new ArrayList<>();
        
        // Personality-based recommendations
        switch (personality) {
            case "DISCIPLINED_SAVER":
                recommendations.add("Consider investing your savings in diversified portfolios");
                recommendations.add("Explore high-yield savings accounts for better returns");
                break;
            case "IMPULSE_BUYER":
                recommendations.add("Set up spending alerts to track purchases in real-time");
                recommendations.add("Consider using the 24-hour rule before making non-essential purchases");
                recommendations.add("Create a budget for discretionary spending");
                break;
            case "BIG_TICKET_BUYER":
                recommendations.add("Compare prices across multiple vendors before large purchases");
                recommendations.add("Consider payment plans for better cash flow management");
                break;
            case "FREQUENT_TRANSACTOR":
                recommendations.add("Consolidate small purchases to reduce transaction fees");
                recommendations.add("Review and cancel unused subscriptions");
                break;
            default:
                recommendations.add("Maintain your balanced spending approach");
        }
        
        // Risk-based recommendations
        if (riskIndicators.containsKey("failureRate")) {
            String failureRateStr = (String) riskIndicators.get("failureRate");
            double failureRate = Double.parseDouble(failureRateStr.replace("%", ""));
            if (failureRate > 5) {
                recommendations.add("Update payment methods to reduce transaction failures");
            }
        }
        
        // Trend-based recommendations
        for (String trend : trends) {
            if (trend.contains("increased") && trend.contains("%")) {
                recommendations.add("Review recent spending increases for optimization opportunities");
            }
        }
        
        return recommendations;
    }
    
    private Map<String, BigDecimal> calculateBehaviorScores(List<TransactionAnalytics> transactions) {
        Map<String, BigDecimal> scores = new HashMap<>();
        
        // Consistency score
        BigDecimal consistencyScore = calculateConsistencyScore(transactions);
        scores.put("consistency", consistencyScore);
        
        // Diversity score
        BigDecimal diversityScore = calculateDiversityScore(transactions);
        scores.put("diversity", diversityScore);
        
        // Efficiency score
        BigDecimal efficiencyScore = calculateEfficiencyScore(transactions);
        scores.put("efficiency", efficiencyScore);
        
        // Overall behavior score
        BigDecimal overallScore = consistencyScore.add(diversityScore).add(efficiencyScore)
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        scores.put("overall", overallScore);
        
        return scores;
    }
    
    private BigDecimal calculateConsistencyScore(List<TransactionAnalytics> transactions) {
        if (transactions.size() < 2) return BigDecimal.valueOf(50);
        
        List<BigDecimal> amounts = transactions.stream()
                .map(TransactionAnalytics::getAmount)
                .collect(Collectors.toList());
        
        BigDecimal mean = amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), RoundingMode.HALF_UP);
        
        BigDecimal variance = calculateVariance(transactions, mean);
        
        // Lower variance = higher consistency score
        if (variance.compareTo(BigDecimal.valueOf(0.2)) < 0) return BigDecimal.valueOf(90);
        if (variance.compareTo(BigDecimal.valueOf(0.4)) < 0) return BigDecimal.valueOf(70);
        if (variance.compareTo(BigDecimal.valueOf(0.6)) < 0) return BigDecimal.valueOf(50);
        return BigDecimal.valueOf(30);
    }
    
    private BigDecimal calculateDiversityScore(List<TransactionAnalytics> transactions) {
        Set<String> uniqueCategories = transactions.stream()
                .map(TransactionAnalytics::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        Set<UUID> uniqueMerchants = transactions.stream()
                .map(TransactionAnalytics::getMerchantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        int diversityCount = uniqueCategories.size() + uniqueMerchants.size();
        
        if (diversityCount > 20) return BigDecimal.valueOf(90);
        if (diversityCount > 15) return BigDecimal.valueOf(70);
        if (diversityCount > 10) return BigDecimal.valueOf(50);
        return BigDecimal.valueOf(30);
    }
    
    private BigDecimal calculateEfficiencyScore(List<TransactionAnalytics> transactions) {
        long successfulCount = transactions.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .count();
        
        double successRate = (double) successfulCount / transactions.size();
        
        return BigDecimal.valueOf(successRate * 100).setScale(0, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateVariance(List<TransactionAnalytics> transactions, BigDecimal mean) {
        if (transactions.size() <= 1) return BigDecimal.ZERO;
        
        BigDecimal sumSquaredDiff = transactions.stream()
                .map(t -> t.getAmount().subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sumSquaredDiff.divide(BigDecimal.valueOf(transactions.size()), RoundingMode.HALF_UP);
    }
    
    private boolean isEssentialCategory(String category) {
        Set<String> essentialCategories = Set.of(
                "GROCERIES", "UTILITIES", "RENT", "MORTGAGE", "INSURANCE",
                "HEALTHCARE", "TRANSPORTATION", "EDUCATION"
        );
        return essentialCategories.contains(category.toUpperCase());
    }
    
    private BigDecimal calculateConfidenceLevel(int dataPoints) {
        if (dataPoints >= 100) return BigDecimal.valueOf(0.95);
        if (dataPoints >= 50) return BigDecimal.valueOf(0.85);
        if (dataPoints >= 20) return BigDecimal.valueOf(0.70);
        if (dataPoints >= 10) return BigDecimal.valueOf(0.50);
        return BigDecimal.valueOf(0.30);
    }
    
    private List<ForecastData> generateForecasts(UUID userId, AnalyticsRequest request) {
        log.debug("Generating ML-based forecasts for user: {}", userId);
        List<ForecastData> forecasts = new ArrayList<>();
        
        try {
            // Get historical data for time series analysis
            LocalDateTime historicalStart = request.getStartDate().minusMonths(6);
            List<TransactionAnalytics> historicalData = analyticsRepository
                    .findByUserIdAndTimestampBetween(userId, historicalStart, request.getEndDate());
            
            if (historicalData.size() < 30) {
                // Not enough data for accurate forecasting
                return generateSimpleForecasts(userId, request);
            }
            
            // Calculate daily aggregates
            Map<LocalDate, BigDecimal> dailyTotals = historicalData.stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getTimestamp().toLocalDate(),
                            Collectors.reducing(BigDecimal.ZERO, 
                                    TransactionAnalytics::getAmount, 
                                    BigDecimal::add)));
            
            // Calculate moving averages
            BigDecimal ma7 = calculateMovingAverage(dailyTotals, 7);
            BigDecimal ma30 = calculateMovingAverage(dailyTotals, 30);
            
            // Calculate trend
            double trendFactor = calculateTrendFactor(dailyTotals);
            
            // Calculate seasonality
            Map<String, BigDecimal> seasonalFactors = calculateSeasonalFactors(historicalData);
            
            // Generate category-specific forecasts
            Map<String, List<TransactionAnalytics>> categoryData = historicalData.stream()
                    .filter(t -> t.getCategory() != null)
                    .collect(Collectors.groupingBy(TransactionAnalytics::getCategory));
            
            // Generate forecasts for next 30 days
            LocalDateTime currentDate = LocalDateTime.now();
            for (int dayOffset = 1; dayOffset <= 30; dayOffset++) {
                LocalDateTime forecastDate = currentDate.plusDays(dayOffset);
                
                // Overall spending forecast
                BigDecimal baseAmount = dayOffset <= 7 ? ma7 : ma30;
                BigDecimal trendAdjustment = baseAmount.multiply(
                        BigDecimal.valueOf(trendFactor * dayOffset / 30));
                
                String dayOfWeek = forecastDate.getDayOfWeek().toString();
                BigDecimal seasonalAdjustment = seasonalFactors.getOrDefault(dayOfWeek, BigDecimal.ONE);
                
                BigDecimal predictedAmount = baseAmount
                        .add(trendAdjustment)
                        .multiply(seasonalAdjustment);
                
                // Calculate confidence based on data quality and forecast distance
                BigDecimal confidence = calculateForecastConfidence(historicalData.size(), dayOffset);
                
                forecasts.add(ForecastData.builder()
                        .forecastDate(forecastDate)
                        .predictedAmount(predictedAmount.setScale(2, RoundingMode.HALF_UP))
                        .confidenceInterval(confidence)
                        .forecastType("SPENDING")
                        .category("ALL")
                        .upperBound(predictedAmount.multiply(BigDecimal.valueOf(1.2)))
                        .lowerBound(predictedAmount.multiply(BigDecimal.valueOf(0.8)))
                        .build());
                
                // Category-specific forecasts for key days
                if (dayOffset % 7 == 0) { // Weekly category forecasts
                    for (Map.Entry<String, List<TransactionAnalytics>> entry : categoryData.entrySet()) {
                        if (entry.getValue().size() >= 10) {
                            BigDecimal categoryAvg = entry.getValue().stream()
                                    .map(TransactionAnalytics::getAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .divide(BigDecimal.valueOf(entry.getValue().size()), RoundingMode.HALF_UP);
                            
                            forecasts.add(ForecastData.builder()
                                    .forecastDate(forecastDate)
                                    .predictedAmount(categoryAvg)
                                    .confidenceInterval(confidence.multiply(BigDecimal.valueOf(0.8)))
                                    .forecastType("CATEGORY_SPENDING")
                                    .category(entry.getKey())
                                    .build());
                        }
                    }
                }
            }
            
            // Sort forecasts by date and type
            forecasts.sort((a, b) -> {
                int dateCompare = a.getForecastDate().compareTo(b.getForecastDate());
                if (dateCompare != 0) return dateCompare;
                return a.getForecastType().compareTo(b.getForecastType());
            });
            
            return forecasts;
            
        } catch (Exception e) {
            log.error("Error generating forecasts for user: {}", userId, e);
            return generateSimpleForecasts(userId, request);
        }
    }
    
    private List<ForecastData> generateSimpleForecasts(UUID userId, AnalyticsRequest request) {
        // Fallback simple forecasting when not enough data
        List<ForecastData> forecasts = new ArrayList<>();
        
        List<TransactionAnalytics> recentTransactions = analyticsRepository
                .findByUserIdAndTimestampBetween(userId, 
                        request.getStartDate(), request.getEndDate());
        
        BigDecimal dailyAverage = BigDecimal.valueOf(100); // Default
        if (!recentTransactions.isEmpty()) {
            BigDecimal total = recentTransactions.stream()
                    .map(TransactionAnalytics::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
            if (days > 0) {
                dailyAverage = total.divide(BigDecimal.valueOf(days), RoundingMode.HALF_UP);
            }
        }
        
        for (int i = 1; i <= 30; i++) {
            forecasts.add(ForecastData.builder()
                    .forecastDate(LocalDateTime.now().plusDays(i))
                    .predictedAmount(dailyAverage)
                    .confidenceInterval(BigDecimal.valueOf(0.5))
                    .forecastType("SPENDING")
                    .category("ALL")
                    .build());
        }
        
        return forecasts;
    }
    
    private BigDecimal calculateMovingAverage(Map<LocalDate, BigDecimal> dailyTotals, int days) {
        List<BigDecimal> recentValues = dailyTotals.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, BigDecimal>comparingByKey().reversed())
                .limit(days)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        
        if (recentValues.isEmpty()) return BigDecimal.ZERO;
        
        return recentValues.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recentValues.size()), RoundingMode.HALF_UP);
    }
    
    private double calculateTrendFactor(Map<LocalDate, BigDecimal> dailyTotals) {
        if (dailyTotals.size() < 10) return 0.0;
        
        List<Map.Entry<LocalDate, BigDecimal>> sortedEntries = dailyTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        
        int n = sortedEntries.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = sortedEntries.get(i).getValue().doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        // Calculate slope using linear regression
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        
        // Normalize to a factor between -0.1 and 0.1
        return Math.max(-0.1, Math.min(0.1, slope / 100));
    }
    
    private Map<String, BigDecimal> calculateSeasonalFactors(List<TransactionAnalytics> transactions) {
        Map<String, List<BigDecimal>> dayOfWeekAmounts = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTimestamp().getDayOfWeek().toString(),
                        Collectors.mapping(TransactionAnalytics::getAmount, Collectors.toList())));
        
        BigDecimal overallAverage = transactions.stream()
                .map(TransactionAnalytics::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(transactions.size()), RoundingMode.HALF_UP);
        
        Map<String, BigDecimal> seasonalFactors = new HashMap<>();
        
        for (Map.Entry<String, List<BigDecimal>> entry : dayOfWeekAmounts.entrySet()) {
            BigDecimal dayAverage = entry.getValue().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(entry.getValue().size()), RoundingMode.HALF_UP);
            
            BigDecimal factor = overallAverage.compareTo(BigDecimal.ZERO) > 0
                    ? dayAverage.divide(overallAverage, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;
            
            seasonalFactors.put(entry.getKey(), factor);
        }
        
        return seasonalFactors;
    }
    
    private BigDecimal calculateForecastConfidence(int dataPoints, int daysAhead) {
        // Base confidence on data quantity
        BigDecimal baseConfidence;
        if (dataPoints >= 180) baseConfidence = BigDecimal.valueOf(0.95);
        else if (dataPoints >= 90) baseConfidence = BigDecimal.valueOf(0.85);
        else if (dataPoints >= 30) baseConfidence = BigDecimal.valueOf(0.70);
        else baseConfidence = BigDecimal.valueOf(0.50);
        
        // Reduce confidence for further forecasts
        BigDecimal distancePenalty = BigDecimal.valueOf(Math.max(0.5, 1.0 - (daysAhead * 0.01)));
        
        return baseConfidence.multiply(distancePenalty).setScale(2, RoundingMode.HALF_UP);
    }
    
    private List<AnomalyDetection> generateAnomalies(UUID userId, AnalyticsRequest request) {
        log.debug("Performing comprehensive anomaly detection for user: {}", userId);
        List<AnomalyDetection> anomalies = new ArrayList<>();
        
        try {
            // Use the anomaly detection service for comprehensive analysis
            List<TransactionAnomaly> detectedAnomalies = anomalyDetectionService
                    .detectAnomalies(userId, request.getStartDate(), request.getEndDate());
            
            // Convert to AnomalyDetection format
            for (TransactionAnomaly anomaly : detectedAnomalies) {
                // Get the transaction details
                TransactionAnalytics transaction = analyticsRepository
                        .findByTransactionId(UUID.fromString(anomaly.getTransactionId()))
                        .orElse(null);
                
                if (transaction != null) {
                    BigDecimal severityScore = convertSeverityToScore(anomaly.getSeverity());
                    
                    anomalies.add(AnomalyDetection.builder()
                            .detectedAt(anomaly.getDetectedAt())
                            .anomalyType(anomaly.getType())
                            .severity(severityScore)
                            .description(anomaly.getDescription())
                            .expectedValue(getExpectedValue(anomaly))
                            .actualValue(transaction.getAmount())
                            .confidence(anomaly.getAnomalyScore())
                            .actionRequired(severityScore.compareTo(BigDecimal.valueOf(0.7)) > 0)
                            .build());
                }
            }
            
            // Additional pattern-based anomaly detection
            List<TransactionAnalytics> transactions = analyticsRepository
                    .findByUserIdAndTimestampBetween(userId, request.getStartDate(), request.getEndDate());
            
            if (transactions.size() >= 10) {
                // Detect sudden spikes
                detectSpendingSpikes(transactions, anomalies);
                
                // Detect unusual merchants
                detectUnusualMerchants(userId, transactions, anomalies);
                
                // Detect velocity anomalies
                detectVelocityAnomalies(transactions, anomalies);
            }
            
            // Sort by severity and recency
            anomalies.sort((a, b) -> {
                int severityCompare = b.getSeverity().compareTo(a.getSeverity());
                if (severityCompare != 0) return severityCompare;
                return b.getDetectedAt().compareTo(a.getDetectedAt());
            });
            
            return anomalies;
            
        } catch (Exception e) {
            log.error("Error detecting anomalies for user: {}", userId, e);
            return anomalies;
        }
    }
    
    private BigDecimal convertSeverityToScore(String severity) {
        switch (severity) {
            case "CRITICAL": return BigDecimal.valueOf(1.0);
            case "HIGH": return BigDecimal.valueOf(0.8);
            case "MEDIUM": return BigDecimal.valueOf(0.6);
            case "LOW": return BigDecimal.valueOf(0.4);
            default: return BigDecimal.valueOf(0.2);
        }
    }
    
    private BigDecimal getExpectedValue(TransactionAnomaly anomaly) {
        if (anomaly.getMetadata() != null && anomaly.getMetadata().containsKey("mean")) {
            return BigDecimal.valueOf((Double) anomaly.getMetadata().get("mean"));
        }
        return BigDecimal.valueOf(100); // Default expected value
    }
    
    private void detectSpendingSpikes(List<TransactionAnalytics> transactions, 
                                     List<AnomalyDetection> anomalies) {
        BigDecimal average = transactions.stream()
                .map(TransactionAnalytics::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(transactions.size()), RoundingMode.HALF_UP);
        
        BigDecimal threshold = average.multiply(BigDecimal.valueOf(3));
        
        for (TransactionAnalytics transaction : transactions) {
            if (transaction.getAmount().compareTo(threshold) > 0) {
                anomalies.add(AnomalyDetection.builder()
                        .detectedAt(transaction.getTimestamp())
                        .anomalyType("SPENDING_SPIKE")
                        .severity(BigDecimal.valueOf(0.7))
                        .description("Transaction amount significantly above average")
                        .expectedValue(average)
                        .actualValue(transaction.getAmount())
                        .confidence(BigDecimal.valueOf(0.85))
                        .build());
            }
        }
    }
    
    private void detectUnusualMerchants(UUID userId, List<TransactionAnalytics> transactions,
                                       List<AnomalyDetection> anomalies) {
        // Get historical merchant frequency
        LocalDateTime historicalStart = transactions.get(0).getTimestamp().minusMonths(3);
        List<TransactionAnalytics> historicalTransactions = analyticsRepository
                .findByUserIdAndTimestampBetween(userId, historicalStart, transactions.get(0).getTimestamp());
        
        Set<UUID> historicalMerchants = historicalTransactions.stream()
                .map(TransactionAnalytics::getMerchantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        for (TransactionAnalytics transaction : transactions) {
            if (transaction.getMerchantId() != null && 
                !historicalMerchants.contains(transaction.getMerchantId())) {
                
                anomalies.add(AnomalyDetection.builder()
                        .detectedAt(transaction.getTimestamp())
                        .anomalyType("NEW_MERCHANT")
                        .severity(BigDecimal.valueOf(0.3))
                        .description("Transaction with previously unused merchant")
                        .expectedValue(BigDecimal.ZERO)
                        .actualValue(transaction.getAmount())
                        .confidence(BigDecimal.valueOf(0.75))
                        .build());
            }
        }
    }
    
    private void detectVelocityAnomalies(List<TransactionAnalytics> transactions,
                                        List<AnomalyDetection> anomalies) {
        // Group transactions by hour
        Map<LocalDateTime, Long> hourlyCount = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTimestamp().truncatedTo(ChronoUnit.HOURS),
                        Collectors.counting()));
        
        for (Map.Entry<LocalDateTime, Long> entry : hourlyCount.entrySet()) {
            if (entry.getValue() > 10) {
                anomalies.add(AnomalyDetection.builder()
                        .detectedAt(entry.getKey())
                        .anomalyType("HIGH_VELOCITY")
                        .severity(BigDecimal.valueOf(0.6))
                        .description("Unusually high transaction frequency: " + entry.getValue() + " transactions in one hour")
                        .expectedValue(BigDecimal.valueOf(3))
                        .actualValue(BigDecimal.valueOf(entry.getValue()))
                        .confidence(BigDecimal.valueOf(0.9))
                        .build());
            }
        }
    }
    
    private RiskAssessment generateRiskAssessment(UUID userId, AnalyticsRequest request) {
        log.debug("Generating comprehensive risk assessment for user: {}", userId);
        
        try {
            // Get user's transaction data
            List<TransactionAnalytics> transactions = analyticsRepository
                    .findByUserIdAndTimestampBetween(userId, request.getStartDate(), request.getEndDate());
            
            if (transactions.isEmpty()) {
                return createLowRiskAssessment("Insufficient transaction data");
            }
            
            // Calculate multiple risk factors
            List<String> riskFactors = new ArrayList<>();
            List<String> mitigationStrategies = new ArrayList<>();
            BigDecimal totalRiskScore = BigDecimal.ZERO;
            
            // 1. Transaction Volume Risk (25% weight)
            BigDecimal volumeRisk = assessTransactionVolumeRisk(transactions, riskFactors);
            totalRiskScore = totalRiskScore.add(volumeRisk.multiply(BigDecimal.valueOf(0.25)));
            
            // 2. Spending Pattern Risk (20% weight)
            BigDecimal patternRisk = assessSpendingPatternRisk(userId, transactions, riskFactors);
            totalRiskScore = totalRiskScore.add(patternRisk.multiply(BigDecimal.valueOf(0.20)));
            
            // 3. Transaction Frequency Risk (15% weight)
            BigDecimal frequencyRisk = assessTransactionFrequencyRisk(transactions, riskFactors);
            totalRiskScore = totalRiskScore.add(frequencyRisk.multiply(BigDecimal.valueOf(0.15)));
            
            // 4. Failed Transaction Risk (15% weight)
            BigDecimal failureRisk = assessFailedTransactionRisk(userId, riskFactors);
            totalRiskScore = totalRiskScore.add(failureRisk.multiply(BigDecimal.valueOf(0.15)));
            
            // 5. Location/Merchant Risk (10% weight)
            BigDecimal locationRisk = assessLocationRisk(transactions, riskFactors);
            totalRiskScore = totalRiskScore.add(locationRisk.multiply(BigDecimal.valueOf(0.10)));
            
            // 6. Time Pattern Risk (10% weight)
            BigDecimal timeRisk = assessTimePatternRisk(transactions, riskFactors);
            totalRiskScore = totalRiskScore.add(timeRisk.multiply(BigDecimal.valueOf(0.10)));
            
            // 7. Historical Anomaly Risk (5% weight)
            BigDecimal anomalyRisk = assessAnomalyRisk(userId, riskFactors);
            totalRiskScore = totalRiskScore.add(anomalyRisk.multiply(BigDecimal.valueOf(0.05)));
            
            // Generate mitigation strategies based on identified risks
            mitigationStrategies.addAll(generateMitigationStrategies(riskFactors, totalRiskScore));
            
            // Determine risk level
            String riskLevel = determineRiskLevel(totalRiskScore);
            
            return RiskAssessment.builder()
                    .riskLevel(riskLevel)
                    .riskScore(totalRiskScore.setScale(3, RoundingMode.HALF_UP))
                    .riskFactors(riskFactors.isEmpty() ? Arrays.asList("No significant risk factors identified") : riskFactors)
                    .mitigationStrategies(mitigationStrategies.isEmpty() ? Arrays.asList("Continue monitoring") : mitigationStrategies)
                    .assessmentDate(LocalDateTime.now())
                    .dataPoints(transactions.size())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating risk assessment for user: {}", userId, e);
            return createErrorRiskAssessment("Error calculating risk: " + e.getMessage());
        }
    }
    
    private BigDecimal assessTransactionVolumeRisk(List<TransactionAnalytics> transactions, List<String> riskFactors) {
        BigDecimal totalVolume = transactions.stream()
                .map(TransactionAnalytics::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal avgDailyVolume = totalVolume.divide(BigDecimal.valueOf(30), RoundingMode.HALF_UP);
        
        // High volume risk thresholds
        if (avgDailyVolume.compareTo(BigDecimal.valueOf(10000)) > 0) {
            riskFactors.add("Very high daily transaction volume: $" + avgDailyVolume);
            return BigDecimal.valueOf(0.9);
        } else if (avgDailyVolume.compareTo(BigDecimal.valueOf(5000)) > 0) {
            riskFactors.add("High daily transaction volume: $" + avgDailyVolume);
            return BigDecimal.valueOf(0.7);
        } else if (avgDailyVolume.compareTo(BigDecimal.valueOf(2000)) > 0) {
            riskFactors.add("Moderate daily transaction volume: $" + avgDailyVolume);
            return BigDecimal.valueOf(0.4);
        }
        
        return BigDecimal.valueOf(0.1);
    }
    
    private BigDecimal assessSpendingPatternRisk(UUID userId, List<TransactionAnalytics> transactions, List<String> riskFactors) {
        // Calculate spending variance
        List<BigDecimal> amounts = transactions.stream()
                .map(TransactionAnalytics::getAmount)
                .collect(Collectors.toList());
        
        if (amounts.size() < 2) return BigDecimal.valueOf(0.1);
        
        BigDecimal mean = amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), RoundingMode.HALF_UP);
        
        BigDecimal variance = amounts.stream()
                .map(amount -> amount.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), RoundingMode.HALF_UP);
        
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        BigDecimal coefficientOfVariation = stdDev.divide(mean, RoundingMode.HALF_UP);
        
        if (coefficientOfVariation.compareTo(BigDecimal.valueOf(2.0)) > 0) {
            riskFactors.add("Very high spending variance (CV: " + coefficientOfVariation.setScale(2, RoundingMode.HALF_UP) + ")");
            return BigDecimal.valueOf(0.8);
        } else if (coefficientOfVariation.compareTo(BigDecimal.valueOf(1.0)) > 0) {
            riskFactors.add("High spending variance (CV: " + coefficientOfVariation.setScale(2, RoundingMode.HALF_UP) + ")");
            return BigDecimal.valueOf(0.5);
        }
        
        return BigDecimal.valueOf(0.2);
    }
    
    private BigDecimal assessTransactionFrequencyRisk(List<TransactionAnalytics> transactions, List<String> riskFactors) {
        Map<String, Long> dailyTransactionCounts = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTimestamp().toLocalDate().toString(),
                        Collectors.counting()));
        
        long maxDailyTransactions = dailyTransactionCounts.values().stream()
                .mapToLong(Long::longValue)
                .max().orElse(0);
        
        if (maxDailyTransactions > 50) {
            riskFactors.add("Very high daily transaction frequency: " + maxDailyTransactions + " transactions");
            return BigDecimal.valueOf(0.9);
        } else if (maxDailyTransactions > 25) {
            riskFactors.add("High daily transaction frequency: " + maxDailyTransactions + " transactions");
            return BigDecimal.valueOf(0.6);
        } else if (maxDailyTransactions > 15) {
            riskFactors.add("Moderate daily transaction frequency: " + maxDailyTransactions + " transactions");
            return BigDecimal.valueOf(0.3);
        }
        
        return BigDecimal.valueOf(0.1);
    }
    
    private BigDecimal assessFailedTransactionRisk(UUID userId, List<String> riskFactors) {
        // Get failed transactions count from last 30 days
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        Long failedCount = analyticsRepository.countFailedTransactions(userId, thirtyDaysAgo, LocalDateTime.now());
        Long totalCount = analyticsRepository.countByUserIdAndTimestampAfter(userId, thirtyDaysAgo);
        
        if (totalCount == 0) return BigDecimal.valueOf(0.1);
        
        double failureRate = failedCount.doubleValue() / totalCount.doubleValue();
        
        if (failureRate > 0.2) {
            riskFactors.add("Very high failure rate: " + String.format("%.1f%%", failureRate * 100));
            return BigDecimal.valueOf(0.9);
        } else if (failureRate > 0.1) {
            riskFactors.add("High failure rate: " + String.format("%.1f%%", failureRate * 100));
            return BigDecimal.valueOf(0.6);
        } else if (failureRate > 0.05) {
            riskFactors.add("Moderate failure rate: " + String.format("%.1f%%", failureRate * 100));
            return BigDecimal.valueOf(0.3);
        }
        
        return BigDecimal.valueOf(0.1);
    }
    
    private BigDecimal assessLocationRisk(List<TransactionAnalytics> transactions, List<String> riskFactors) {
        Set<String> uniqueLocations = transactions.stream()
                .map(TransactionAnalytics::getLocation)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        int locationCount = uniqueLocations.size();
        
        if (locationCount > 20) {
            riskFactors.add("Very high location diversity: " + locationCount + " unique locations");
            return BigDecimal.valueOf(0.7);
        } else if (locationCount > 10) {
            riskFactors.add("High location diversity: " + locationCount + " unique locations");
            return BigDecimal.valueOf(0.4);
        }
        
        return BigDecimal.valueOf(0.1);
    }
    
    private BigDecimal assessTimePatternRisk(List<TransactionAnalytics> transactions, List<String> riskFactors) {
        long offHoursTransactions = transactions.stream()
                .filter(t -> {
                    int hour = t.getTimestamp().getHour();
                    return hour < 6 || hour > 23;
                })
                .count();
        
        double offHoursRatio = (double) offHoursTransactions / transactions.size();
        
        if (offHoursRatio > 0.3) {
            riskFactors.add("High off-hours activity: " + String.format("%.1f%%", offHoursRatio * 100));
            return BigDecimal.valueOf(0.6);
        } else if (offHoursRatio > 0.15) {
            riskFactors.add("Moderate off-hours activity: " + String.format("%.1f%%", offHoursRatio * 100));
            return BigDecimal.valueOf(0.3);
        }
        
        return BigDecimal.valueOf(0.1);
    }
    
    private BigDecimal assessAnomalyRisk(UUID userId, List<String> riskFactors) {
        // Check for recent anomalies
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        try {
            List<TransactionAnomaly> recentAnomalies = anomalyDetectionService
                    .detectAnomalies(userId, last30Days, LocalDateTime.now());
            
            long criticalAnomalies = recentAnomalies.stream()
                    .filter(a -> "CRITICAL".equals(a.getSeverity()) || "HIGH".equals(a.getSeverity()))
                    .count();
            
            if (criticalAnomalies > 5) {
                riskFactors.add("Multiple critical anomalies detected: " + criticalAnomalies);
                return BigDecimal.valueOf(0.8);
            } else if (criticalAnomalies > 2) {
                riskFactors.add("Some critical anomalies detected: " + criticalAnomalies);
                return BigDecimal.valueOf(0.5);
            } else if (!recentAnomalies.isEmpty()) {
                riskFactors.add("Minor anomalies detected: " + recentAnomalies.size());
                return BigDecimal.valueOf(0.2);
            }
        } catch (Exception e) {
            log.warn("Could not assess anomaly risk for user: {}", userId, e);
        }
        
        return BigDecimal.valueOf(0.1);
    }
    
    private List<String> generateMitigationStrategies(List<String> riskFactors, BigDecimal riskScore) {
        List<String> strategies = new ArrayList<>();
        
        if (riskScore.compareTo(BigDecimal.valueOf(0.7)) > 0) {
            strategies.add("Implement enhanced transaction monitoring");
            strategies.add("Require additional authentication for high-value transactions");
            strategies.add("Set daily transaction limits");
            strategies.add("Enable real-time fraud alerts");
        } else if (riskScore.compareTo(BigDecimal.valueOf(0.4)) > 0) {
            strategies.add("Enable transaction notifications");
            strategies.add("Review spending patterns monthly");
            strategies.add("Consider setting transaction limits");
        } else {
            strategies.add("Continue regular monitoring");
            strategies.add("Review quarterly");
        }
        
        // Add specific strategies based on risk factors
        for (String factor : riskFactors) {
            if (factor.contains("volume")) {
                strategies.add("Consider implementing transaction limits");
            }
            if (factor.contains("frequency")) {
                strategies.add("Monitor for potential card skimming or account compromise");
            }
            if (factor.contains("failure")) {
                strategies.add("Review account security and update payment methods");
            }
            if (factor.contains("location")) {
                strategies.add("Enable location-based alerts");
            }
            if (factor.contains("off-hours")) {
                strategies.add("Consider time-based transaction restrictions");
            }
        }
        
        return strategies.stream().distinct().collect(Collectors.toList());
    }
    
    private String determineRiskLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(BigDecimal.valueOf(0.8)) > 0) {
            return "CRITICAL";
        } else if (riskScore.compareTo(BigDecimal.valueOf(0.6)) > 0) {
            return "HIGH";
        } else if (riskScore.compareTo(BigDecimal.valueOf(0.4)) > 0) {
            return "MEDIUM";
        } else if (riskScore.compareTo(BigDecimal.valueOf(0.2)) > 0) {
            return "LOW";
        } else {
            return "MINIMAL";
        }
    }
    
    private RiskAssessment createLowRiskAssessment(String reason) {
        return RiskAssessment.builder()
                .riskLevel("LOW")
                .riskScore(BigDecimal.valueOf(0.1))
                .riskFactors(Arrays.asList(reason))
                .mitigationStrategies(Arrays.asList("Continue monitoring", "Collect more transaction data"))
                .assessmentDate(LocalDateTime.now())
                .dataPoints(0)
                .build();
    }
    
    private RiskAssessment createErrorRiskAssessment(String error) {
        return RiskAssessment.builder()
                .riskLevel("UNKNOWN")
                .riskScore(BigDecimal.valueOf(0.5))
                .riskFactors(Arrays.asList("Assessment error: " + error))
                .mitigationStrategies(Arrays.asList("Manual review required", "Retry assessment"))
                .assessmentDate(LocalDateTime.now())
                .dataPoints(0)
                .build();
    }

    // Additional helper methods

    private CategoryBehavior analyzeCategoryBehavior(UUID userId, String category, AnalyticsRequest request) {
        log.debug("Analyzing category behavior for user: {}, category: {}", userId, category);

        // Get historical data for this category
        LocalDateTime historicalStart = request.getStartDate().minusMonths(6);
        List<TransactionAnalytics> categoryTransactions = analyticsRepository
                .findByUserIdAndTimestampBetween(userId, historicalStart, request.getEndDate())
                .stream()
                .filter(t -> category.equals(t.getCategory()))
                .collect(Collectors.toList());

        if (categoryTransactions.isEmpty()) {
            return CategoryBehavior.builder()
                    .category(category)
                    .frequency("RARE")
                    .regularity("IRREGULAR")
                    .averageAmount(BigDecimal.ZERO)
                    .trend("STABLE")
                    .build();
        }

        // Calculate frequency
        long daysBetween = ChronoUnit.DAYS.between(historicalStart, request.getEndDate());
        double transactionsPerWeek = (categoryTransactions.size() * 7.0) / daysBetween;
        String frequency = transactionsPerWeek > 2 ? "FREQUENT" : transactionsPerWeek > 0.5 ? "REGULAR" : "RARE";

        // Calculate regularity
        List<Long> daysBetweenTransactions = new ArrayList<>();
        for (int i = 1; i < categoryTransactions.size(); i++) {
            long daysDiff = ChronoUnit.DAYS.between(
                    categoryTransactions.get(i - 1).getTimestamp(),
                    categoryTransactions.get(i).getTimestamp());
            daysBetweenTransactions.add(daysDiff);
        }

        double avgDaysBetween = daysBetweenTransactions.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        double stdDev = calculateStandardDeviation(daysBetweenTransactions);
        double coefficientOfVariation = avgDaysBetween > 0 ? stdDev / avgDaysBetween : 0;
        String regularity = coefficientOfVariation < 0.3 ? "REGULAR" : coefficientOfVariation < 0.7 ? "SOMEWHAT_REGULAR" : "IRREGULAR";

        // Calculate average amount
        BigDecimal avgAmount = categoryTransactions.stream()
                .map(TransactionAnalytics::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(categoryTransactions.size()), 2, RoundingMode.HALF_UP);

        // Calculate trend
        String trend = calculateCategoryTrend(categoryTransactions);

        return CategoryBehavior.builder()
                .category(category)
                .frequency(frequency)
                .regularity(regularity)
                .averageAmount(avgAmount)
                .trend(trend)
                .build();
    }

    private String calculateCategoryTrend(List<TransactionAnalytics> transactions) {
        if (transactions.size() < 4) return "STABLE";

        int midpoint = transactions.size() / 2;
        BigDecimal firstHalf = transactions.subList(0, midpoint).stream()
                .map(TransactionAnalytics::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal secondHalf = transactions.subList(midpoint, transactions.size()).stream()
                .map(TransactionAnalytics::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal change = secondHalf.subtract(firstHalf)
                .divide(firstHalf.compareTo(BigDecimal.ZERO) > 0 ? firstHalf : BigDecimal.ONE, 4, RoundingMode.HALF_UP);

        if (change.compareTo(BigDecimal.valueOf(0.1)) > 0) return "INCREASING";
        if (change.compareTo(BigDecimal.valueOf(-0.1)) < 0) return "DECREASING";
        return "STABLE";
    }

    private double calculateStandardDeviation(List<Long> values) {
        if (values.isEmpty()) return 0;

        double mean = values.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    private BigDecimal calculateTotalSpending(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
        return analyticsRepository.getTotalAmountByUserAndDateRange(userId, startDate, endDate);
    }

    private BigDecimal getPreviousMonthCategorySpending(UUID userId, String category, LocalDateTime currentStart) {
        LocalDateTime previousStart = currentStart.minusMonths(1);
        LocalDateTime previousEnd = currentStart.minusDays(1);

        List<Object[]> categoryData = analyticsRepository.getCategorySpending(userId, previousStart, previousEnd);

        return categoryData.stream()
                .filter(row -> category.equals(row[0]))
                .map(row -> (BigDecimal) row[1])
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calculatePercentageChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePatternConfidence(List<Transaction> transactions) {
        if (transactions.size() < 3) return BigDecimal.valueOf(0.3);
        if (transactions.size() < 10) return BigDecimal.valueOf(0.6);
        return BigDecimal.valueOf(0.85);
    }

    private SpendingPattern createTimeBasedPattern(String timeOfDay, List<Transaction> transactions) {
        BigDecimal avgAmount = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);

        Map<String, Long> categoryFreq = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.counting()));

        return SpendingPattern.builder()
                .patternName(timeOfDay + " Spending Pattern")
                .description("Typical spending during " + timeOfDay.toLowerCase())
                .frequency(BigDecimal.valueOf(transactions.size())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .averageAmount(avgAmount)
                .categories(categoryFreq.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(3)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList()))
                .timePattern(timeOfDay)
                .confidence(calculatePatternConfidence(transactions))
                .build();
    }

    private RecurringPattern detectRecurringPattern(List<Transaction> transactions) {
        if (transactions.size() < 3) return null;

        // Check if amounts are similar (within 10%)
        BigDecimal avgAmount = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);

        boolean similarAmounts = transactions.stream()
                .allMatch(t -> {
                    BigDecimal diff = t.getAmount().subtract(avgAmount).abs();
                    BigDecimal threshold = avgAmount.multiply(BigDecimal.valueOf(0.1));
                    return diff.compareTo(threshold) <= 0;
                });

        if (!similarAmounts) return null;

        // Check for regular intervals
        List<Long> daysBetween = new ArrayList<>();
        for (int i = 1; i < transactions.size(); i++) {
            long days = ChronoUnit.DAYS.between(
                    transactions.get(i - 1).getTransactionDate(),
                    transactions.get(i).getTransactionDate());
            daysBetween.add(days);
        }

        double avgInterval = daysBetween.stream().mapToLong(Long::longValue).average().orElse(0);
        double stdDev = calculateStandardDeviation(daysBetween);

        if (stdDev / avgInterval > 0.3) return null; // Too irregular

        String frequency;
        if (avgInterval < 8) frequency = "WEEKLY";
        else if (avgInterval < 16) frequency = "BIWEEKLY";
        else if (avgInterval < 35) frequency = "MONTHLY";
        else return null;

        return RecurringPattern.builder()
                .merchant(transactions.get(0).getMerchant())
                .amount(avgAmount)
                .frequency(frequency)
                .confidence(BigDecimal.valueOf(0.8))
                .build();
    }

    // Mapper methods to convert Object[] to DTOs

    private List<CategorySpending> mapToCategorySpending(List<Object[]> results) {
        return results.stream()
                .map(row -> CategorySpending.builder()
                        .categoryName((String) row[0])
                        .amount((BigDecimal) row[1])
                        .transactionCount(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<DailySpending> mapToDailySpending(List<Object[]> results) {
        return results.stream()
                .map(row -> {
                    BigDecimal amount = (BigDecimal) row[1];
                    long count = ((Number) row[2]).longValue();
                    BigDecimal avgPerTransaction = count > 0 ?
                            amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO;

                    return DailySpending.builder()
                            .date(((java.sql.Date) row[0]).toLocalDate())
                            .amount(amount)
                            .transactionCount(count)
                            .averagePerTransaction(avgPerTransaction)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<HourlySpending> mapToHourlySpending(List<Object[]> results) {
        return results.stream()
                .map(row -> {
                    Integer hour = ((Number) row[0]).intValue();
                    BigDecimal amount = (BigDecimal) row[1];
                    long count = ((Number) row[2]).longValue();
                    BigDecimal avgPerTransaction = count > 0 ?
                            amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO;

                    return HourlySpending.builder()
                            .hour(hour)
                            .amount(amount)
                            .transactionCount(count)
                            .averagePerTransaction(avgPerTransaction)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<IncomeSource> mapToIncomeSources(List<Object[]> results) {
        return results.stream()
                .map(row -> IncomeSource.builder()
                        .source((String) row[0])
                        .amount((BigDecimal) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    private List<DailyIncome> mapToDailyIncome(List<Object[]> results) {
        return results.stream()
                .map(row -> {
                    BigDecimal amount = (BigDecimal) row[1];
                    long count = ((Number) row[2]).longValue();

                    return DailyIncome.builder()
                            .date(((java.sql.Date) row[0]).toLocalDate())
                            .amount(amount)
                            .transactionCount(count)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<CashFlowData> mapToCashFlowData(List<Object[]> results) {
        return results.stream()
                .map(row -> CashFlowData.builder()
                        .date(((java.sql.Date) row[0]).toLocalDate())
                        .netFlow((BigDecimal) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    private List<MerchantSpending> mapToMerchantSpending(List<Object[]> results) {
        return results.stream()
                .map(row -> {
                    String merchantName = (String) row[0];
                    BigDecimal total = (BigDecimal) row[1];
                    long count = ((Number) row[2]).longValue();

                    return MerchantSpending.builder()
                            .merchantName(merchantName)
                            .totalSpent(total)
                            .transactionCount(count)
                            .averageTransactionAmount(count > 0 ?
                                    total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) :
                                    BigDecimal.ZERO)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<MerchantFrequency> mapToMerchantFrequency(List<Object[]> results) {
        return results.stream()
                .map(row -> MerchantFrequency.builder()
                        .merchantName((String) row[0])
                        .frequency(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<MerchantLoyalty> analyzeMerchantLoyalty(UUID userId, AnalyticsRequest request) {
        log.debug("Analyzing merchant loyalty for user: {}", userId);

        // Get all transactions in the period
        List<TransactionAnalytics> transactions = analyticsRepository
                .findByUserIdAndTimestampBetween(userId, request.getStartDate(), request.getEndDate());

        // Group by merchant and analyze loyalty patterns
        Map<String, List<TransactionAnalytics>> merchantTransactions = transactions.stream()
                .filter(t -> t.getMerchantName() != null && !t.getMerchantName().isEmpty())
                .collect(Collectors.groupingBy(TransactionAnalytics::getMerchantName));

        return merchantTransactions.entrySet().stream()
                .map(entry -> {
                    String merchant = entry.getKey();
                    List<TransactionAnalytics> merchantTxns = entry.getValue();

                    // Calculate loyalty metrics
                    long transactionCount = merchantTxns.size();
                    BigDecimal totalSpent = merchantTxns.stream()
                            .map(TransactionAnalytics::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Calculate visit frequency (days between visits)
                    List<LocalDateTime> visitDates = merchantTxns.stream()
                            .map(TransactionAnalytics::getTimestamp)
                            .sorted()
                            .collect(Collectors.toList());

                    double avgDaysBetweenVisits = 0;
                    if (visitDates.size() > 1) {
                        List<Long> daysBetween = new ArrayList<>();
                        for (int i = 1; i < visitDates.size(); i++) {
                            long days = ChronoUnit.DAYS.between(visitDates.get(i - 1), visitDates.get(i));
                            daysBetween.add(days);
                        }
                        avgDaysBetweenVisits = daysBetween.stream()
                                .mapToLong(Long::longValue)
                                .average()
                                .orElse(0);
                    }

                    // Determine loyalty level
                    String loyaltyLevel;
                    if (transactionCount >= 10 && avgDaysBetweenVisits < 7) {
                        loyaltyLevel = "VERY_LOYAL";
                    } else if (transactionCount >= 5 && avgDaysBetweenVisits < 14) {
                        loyaltyLevel = "LOYAL";
                    } else if (transactionCount >= 3) {
                        loyaltyLevel = "REGULAR";
                    } else {
                        loyaltyLevel = "OCCASIONAL";
                    }

                    return MerchantLoyalty.builder()
                            .merchantName(merchant)
                            .visitCount(transactionCount)
                            .totalSpent(totalSpent)
                            .averageDaysBetweenVisits(BigDecimal.valueOf(avgDaysBetweenVisits))
                            .loyaltyLevel(loyaltyLevel)
                            .lastVisit(visitDates.get(visitDates.size() - 1))
                            .firstVisit(visitDates.get(0))
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getVisitCount(), a.getVisitCount()))
                .collect(Collectors.toList());
    }
}

enum SpendingTrend {
    INCREASING, DECREASING, STABLE
}

enum IncomeStability {
    STABLE, VOLATILE, GROWING, DECLINING
}

enum CashFlowTrend {
    IMPROVING, DECLINING, STABLE, VOLATILE
}