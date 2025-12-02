package com.waqiti.investment.service;

import com.waqiti.common.cache.CacheService;
import com.waqiti.investment.domain.InvestmentAccount;
import com.waqiti.investment.domain.InvestmentHolding;
import com.waqiti.investment.domain.Portfolio;
import com.waqiti.investment.dto.analytics.*;
import com.waqiti.investment.repository.InvestmentAccountRepository;
import com.waqiti.investment.repository.InvestmentHoldingRepository;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waqiti.common.financial.BigDecimalMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedAnalyticsService {

    private final InvestmentAccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final InvestmentHoldingRepository holdingRepository;
    private final InvestmentOrderRepository orderRepository;
    private final MarketDataService marketDataService;
    private final CacheService cacheService;

    @Transactional(readOnly = true)
    public AdvancedPortfolioAnalytics getAdvancedPortfolioAnalytics(String accountId) {
        log.info("Generating advanced portfolio analytics for account: {}", accountId);
        
        String cacheKey = cacheService.buildKey("portfolio-analytics", accountId);
        AdvancedPortfolioAnalytics cached = cacheService.get(cacheKey, AdvancedPortfolioAnalytics.class);
        if (cached != null) {
            return cached;
        }
        
        InvestmentAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        
        List<InvestmentHolding> holdings = holdingRepository.findByAccountId(accountId);
        Portfolio portfolio = portfolioRepository.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));
        
        AdvancedPortfolioAnalytics analytics = AdvancedPortfolioAnalytics.builder()
                .accountId(accountId)
                .totalValue(portfolio.getTotalValue())
                .totalCost(portfolio.getTotalCost())
                .totalGainLoss(portfolio.getTotalGainLoss())
                .totalGainLossPercent(portfolio.getTotalGainLossPercent())
                
                // Risk metrics
                .riskMetrics(calculateRiskMetrics(holdings))
                
                // Diversification analysis
                .diversificationAnalysis(calculateDiversificationAnalysis(holdings))
                
                // Performance attribution
                .performanceAttribution(calculatePerformanceAttribution(holdings))
                
                // Sector allocation
                .sectorAllocation(calculateSectorAllocation(holdings))
                
                // Asset class allocation
                .assetClassAllocation(calculateAssetClassAllocation(holdings))
                
                // Geographic allocation
                .geographicAllocation(calculateGeographicAllocation(holdings))
                
                // Holdings analysis
                .holdingsAnalysis(calculateHoldingsAnalysis(holdings))
                
                // Correlation matrix
                .correlationMatrix(calculateCorrelationMatrix(holdings))
                
                // Stress testing
                .stressTestResults(performStressTests(holdings))
                
                // ESG analysis
                .esgAnalysis(calculateESGAnalysis(holdings))
                
                .lastUpdated(Instant.now())
                .build();
        
        // Cache for 30 minutes
        cacheService.set(cacheKey, analytics, Duration.ofMinutes(30));
        
        return analytics;
    }

    @Transactional(readOnly = true)
    public RiskAssessmentReport generateRiskAssessment(String accountId) {
        log.info("Generating risk assessment for account: {}", accountId);
        
        List<InvestmentHolding> holdings = holdingRepository.findByAccountId(accountId);
        List<PerformanceDataPoint> historicalReturns = getHistoricalReturns(accountId, 252); // 1 year
        
        return RiskAssessmentReport.builder()
                .accountId(accountId)
                .overallRiskScore(calculateOverallRiskScore(holdings))
                .volatilityAnalysis(calculateVolatilityAnalysis(historicalReturns))
                .valueAtRisk(calculateValueAtRisk(holdings, historicalReturns))
                .expectedShortfall(calculateExpectedShortfall(holdings, historicalReturns))
                .maxDrawdown(calculateMaxDrawdown(historicalReturns))
                .beta(calculatePortfolioBeta(holdings))
                .sharpeRatio(calculateSharpeRatio(historicalReturns))
                .sortinoRatio(calculateSortinoRatio(historicalReturns))
                .calmarRatio(calculateCalmarRatio(historicalReturns))
                .riskContribution(calculateRiskContribution(holdings))
                .concentrationRisk(calculateConcentrationRisk(holdings))
                .liquidityRisk(calculateLiquidityRisk(holdings))
                .currencyRisk(calculateCurrencyRisk(holdings))
                .sectorRisk(calculateSectorRisk(holdings))
                .recommendations(generateRiskRecommendations(holdings))
                .lastUpdated(Instant.now())
                .build();
    }

    @Transactional(readOnly = true)
    public OptimizationRecommendations getOptimizationRecommendations(String accountId) {
        log.info("Generating optimization recommendations for account: {}", accountId);
        
        List<InvestmentHolding> holdings = holdingRepository.findByAccountId(accountId);
        InvestmentAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        
        return OptimizationRecommendations.builder()
                .accountId(accountId)
                .rebalancingRecommendations(generateRebalancingRecommendations(holdings, account))
                .taxOptimizationSuggestions(generateTaxOptimizationSuggestions(holdings))
                .costReductionOpportunities(identifyCostReductionOpportunities(holdings))
                .diversificationImprovements(suggestDiversificationImprovements(holdings))
                .riskAdjustments(suggestRiskAdjustments(holdings, account))
                .assetAllocationOptimization(optimizeAssetAllocation(holdings, account))
                .portfolioEfficiencyScore(calculatePortfolioEfficiencyScore(holdings))
                .implementationPriority(prioritizeRecommendations(holdings))
                .estimatedImpact(estimateRecommendationImpact(holdings))
                .lastUpdated(Instant.now())
                .build();
    }

    @Transactional(readOnly = true)
    public PerformanceBenchmarkAnalysis benchmarkPerformance(String accountId, String benchmarkSymbol) {
        log.info("Benchmarking performance for account: {} against: {}", accountId, benchmarkSymbol);
        
        List<PerformanceDataPoint> portfolioReturns = getHistoricalReturns(accountId, 252);
        List<PerformanceDataPoint> benchmarkReturns = marketDataService.getHistoricalReturns(benchmarkSymbol, 252);
        
        return PerformanceBenchmarkAnalysis.builder()
                .accountId(accountId)
                .benchmarkSymbol(benchmarkSymbol)
                .portfolioReturns(portfolioReturns)
                .benchmarkReturns(benchmarkReturns)
                .alpha(calculateAlpha(portfolioReturns, benchmarkReturns))
                .beta(calculateBeta(portfolioReturns, benchmarkReturns))
                .rSquared(calculateRSquared(portfolioReturns, benchmarkReturns))
                .trackingError(calculateTrackingError(portfolioReturns, benchmarkReturns))
                .informationRatio(calculateInformationRatio(portfolioReturns, benchmarkReturns))
                .upCaptureRatio(calculateUpCaptureRatio(portfolioReturns, benchmarkReturns))
                .downCaptureRatio(calculateDownCaptureRatio(portfolioReturns, benchmarkReturns))
                .outperformancePeriods(identifyOutperformancePeriods(portfolioReturns, benchmarkReturns))
                .attribution(performAttributionAnalysis(portfolioReturns, benchmarkReturns))
                .lastUpdated(Instant.now())
                .build();
    }

    @Transactional(readOnly = true)
    public MarketSentimentAnalysis getMarketSentimentAnalysis(String accountId) {
        log.info("Analyzing market sentiment for account: {}", accountId);
        
        List<InvestmentHolding> holdings = holdingRepository.findByAccountId(accountId);
        
        Map<String, SentimentMetrics> holdingSentiment = holdings.stream()
                .collect(Collectors.toMap(
                        InvestmentHolding::getSymbol,
                        holding -> marketDataService.getSentimentMetrics(holding.getSymbol())
                ));
        
        return MarketSentimentAnalysis.builder()
                .accountId(accountId)
                .overallSentimentScore(calculateOverallSentimentScore(holdingSentiment))
                .holdingSentiment(holdingSentiment)
                .sectorSentiment(calculateSectorSentiment(holdings, holdingSentiment))
                .fearGreedIndex(marketDataService.getFearGreedIndex())
                .volatilityIndex(marketDataService.getVolatilityIndex())
                .putCallRatio(marketDataService.getPutCallRatio())
                .sentimentTrends(analyzeSentimentTrends(holdingSentiment))
                .riskAdjustment(calculateSentimentBasedRiskAdjustment(holdingSentiment))
                .lastUpdated(Instant.now())
                .build();
    }

    // Risk metrics calculation methods
    private RiskMetrics calculateRiskMetrics(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return RiskMetrics.builder().build();
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate portfolio beta
        BigDecimal portfolioBeta = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    BigDecimal beta = marketDataService.getBeta(holding.getSymbol());
                    return weight.multiply(beta);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate portfolio volatility
        BigDecimal portfolioVolatility = calculatePortfolioVolatility(holdings);
        
        return RiskMetrics.builder()
                .portfolioBeta(portfolioBeta)
                .portfolioVolatility(portfolioVolatility)
                .valueAtRisk95(calculateVaR(holdings, 0.95))
                .valueAtRisk99(calculateVaR(holdings, 0.99))
                .expectedShortfall(calculateES(holdings, 0.95))
                .conditionalValueAtRisk(calculateCVaR(holdings, 0.95))
                .build();
    }

    private DiversificationAnalysis calculateDiversificationAnalysis(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return DiversificationAnalysis.builder().build();
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate Herfindahl-Hirschman Index
        BigDecimal hhi = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    return weight.multiply(weight);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate effective number of holdings
        BigDecimal effectiveHoldings = BigDecimal.ONE.divide(hhi, 2, RoundingMode.HALF_UP);
        
        // Calculate sector concentration
        Map<String, BigDecimal> sectorWeights = calculateSectorWeights(holdings);
        BigDecimal sectorConcentration = sectorWeights.values().stream()
                .map(weight -> weight.multiply(weight))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return DiversificationAnalysis.builder()
                .numberOfHoldings(holdings.size())
                .effectiveNumberOfHoldings(effectiveHoldings)
                .herfindalHirschmanIndex(hhi)
                .sectorConcentration(sectorConcentration)
                .largestHoldingWeight(calculateLargestHoldingWeight(holdings))
                .top5HoldingsWeight(calculateTop5HoldingsWeight(holdings))
                .diversificationRatio(calculateDiversificationRatio(holdings))
                .concentrationScore(calculateConcentrationScore(holdings))
                .build();
    }

    private PerformanceAttribution calculatePerformanceAttribution(List<InvestmentHolding> holdings) {
        Map<String, BigDecimal> sectorContribution = new HashMap<>();
        Map<String, BigDecimal> securityContribution = new HashMap<>();
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        for (InvestmentHolding holding : holdings) {
            BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
            BigDecimal contribution = weight.multiply(holding.getGainLossPercent());
            
            String sector = marketDataService.getSector(holding.getSymbol());
            sectorContribution.merge(sector, contribution, BigDecimal::add);
            securityContribution.put(holding.getSymbol(), contribution);
        }
        
        return PerformanceAttribution.builder()
                .sectorContribution(sectorContribution)
                .securityContribution(securityContribution)
                .allocationEffect(calculateAllocationEffect(holdings))
                .selectionEffect(calculateSelectionEffect(holdings))
                .interactionEffect(calculateInteractionEffect(holdings))
                .build();
    }

    private Map<String, BigDecimal> calculateSectorAllocation(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return new HashMap<>();
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return holdings.stream()
                .collect(Collectors.groupingBy(
                        holding -> marketDataService.getSector(holding.getSymbol()),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                InvestmentHolding::getMarketValue,
                                BigDecimal::add
                        )
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                ));
    }

    private Map<String, BigDecimal> calculateAssetClassAllocation(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return new HashMap<>();
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return holdings.stream()
                .collect(Collectors.groupingBy(
                        holding -> marketDataService.getAssetClass(holding.getSymbol()),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                InvestmentHolding::getMarketValue,
                                BigDecimal::add
                        )
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                ));
    }

    private Map<String, BigDecimal> calculateGeographicAllocation(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return new HashMap<>();
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return holdings.stream()
                .collect(Collectors.groupingBy(
                        holding -> marketDataService.getCountry(holding.getSymbol()),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                InvestmentHolding::getMarketValue,
                                BigDecimal::add
                        )
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                ));
    }

    private HoldingsAnalysis calculateHoldingsAnalysis(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return HoldingsAnalysis.builder().build();
        }
        
        // Find top and bottom performers
        List<InvestmentHolding> sortedByPerformance = holdings.stream()
                .sorted(Comparator.comparing(InvestmentHolding::getGainLossPercent).reversed())
                .collect(Collectors.toList());
        
        List<String> topPerformers = sortedByPerformance.stream()
                .limit(5)
                .map(InvestmentHolding::getSymbol)
                .collect(Collectors.toList());
        
        List<String> bottomPerformers = sortedByPerformance.stream()
                .skip(Math.max(0, sortedByPerformance.size() - 5))
                .map(InvestmentHolding::getSymbol)
                .collect(Collectors.toList());
        
        // Calculate average metrics
        BigDecimal avgGainLoss = holdings.stream()
                .map(InvestmentHolding::getGainLossPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(holdings.size()), 2, RoundingMode.HALF_UP);
        
        return HoldingsAnalysis.builder()
                .totalHoldings(holdings.size())
                .topPerformers(topPerformers)
                .bottomPerformers(bottomPerformers)
                .averageGainLoss(avgGainLoss)
                .winnersCount(holdings.stream().mapToInt(h -> h.getGainLoss().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0).sum())
                .losersCount(holdings.stream().mapToInt(h -> h.getGainLoss().compareTo(BigDecimal.ZERO) < 0 ? 1 : 0).sum())
                .build();
    }

    private Map<String, Map<String, BigDecimal>> calculateCorrelationMatrix(List<InvestmentHolding> holdings) {
        Map<String, Map<String, BigDecimal>> correlationMatrix = new HashMap<>();
        
        for (InvestmentHolding holding1 : holdings) {
            Map<String, BigDecimal> correlations = new HashMap<>();
            
            for (InvestmentHolding holding2 : holdings) {
                BigDecimal correlation = marketDataService.getCorrelation(
                        holding1.getSymbol(), holding2.getSymbol(), 252);
                correlations.put(holding2.getSymbol(), correlation);
            }
            
            correlationMatrix.put(holding1.getSymbol(), correlations);
        }
        
        return correlationMatrix;
    }

    private StressTestResults performStressTests(List<InvestmentHolding> holdings) {
        Map<String, BigDecimal> scenarios = new HashMap<>();
        
        // Market crash scenario (-30%)
        BigDecimal marketCrashImpact = calculateScenarioImpact(holdings, "MARKET_CRASH", new BigDecimal("-0.30"));
        scenarios.put("Market Crash (-30%)", marketCrashImpact);
        
        // Interest rate shock (+200 bps)
        BigDecimal interestRateImpact = calculateScenarioImpact(holdings, "INTEREST_RATE_SHOCK", new BigDecimal("0.02"));
        scenarios.put("Interest Rate Shock (+200 bps)", interestRateImpact);
        
        // Currency crisis
        BigDecimal currencyCrisisImpact = calculateScenarioImpact(holdings, "CURRENCY_CRISIS", new BigDecimal("-0.15"));
        scenarios.put("Currency Crisis", currencyCrisisImpact);
        
        // Sector rotation
        BigDecimal sectorRotationImpact = calculateScenarioImpact(holdings, "SECTOR_ROTATION", BigDecimal.ZERO);
        scenarios.put("Sector Rotation", sectorRotationImpact);
        
        return StressTestResults.builder()
                .scenarios(scenarios)
                .worstCaseScenario(scenarios.entrySet().stream()
                        .min(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("N/A"))
                .expectedMaxLoss(scenarios.values().stream()
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO))
                .portfolioResilience(calculatePortfolioResilience(scenarios))
                .build();
    }

    private ESGAnalysis calculateESGAnalysis(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return ESGAnalysis.builder().build();
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal weightedESGScore = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    BigDecimal esgScore = marketDataService.getESGScore(holding.getSymbol());
                    return weight.multiply(esgScore);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, BigDecimal> sectorESGScores = calculateSectorESGScores(holdings);
        Map<String, BigDecimal> esgMetrics = calculateESGMetrics(holdings);
        
        return ESGAnalysis.builder()
                .overallESGScore(weightedESGScore)
                .environmentalScore(esgMetrics.getOrDefault("Environmental", BigDecimal.ZERO))
                .socialScore(esgMetrics.getOrDefault("Social", BigDecimal.ZERO))
                .governanceScore(esgMetrics.getOrDefault("Governance", BigDecimal.ZERO))
                .sectorESGScores(sectorESGScores)
                .sustainabilityRating(calculateSustainabilityRating(weightedESGScore))
                .controversyLevel(calculateControversyLevel(holdings))
                .carbonFootprint(calculateCarbonFootprint(holdings))
                .build();
    }

    // Helper methods for complex calculations
    private BigDecimal calculatePortfolioVolatility(List<InvestmentHolding> holdings) {
        // This would calculate portfolio volatility using covariance matrix
        // Simplified implementation
        return holdings.stream()
                .map(holding -> marketDataService.getVolatility(holding.getSymbol()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(holdings.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVaR(List<InvestmentHolding> holdings, double confidenceLevel) {
        // Monte Carlo or historical simulation VaR calculation
        // Simplified implementation
        BigDecimal portfolioValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal portfolioVolatility = calculatePortfolioVolatility(holdings);
        double zScore = confidenceLevel == 0.95 ? 1.645 : 2.326; // 95% or 99%
        
        return portfolioValue.multiply(portfolioVolatility)
                .multiply(BigDecimal.valueOf(zScore / 100));
    }

    private List<PerformanceDataPoint> getHistoricalReturns(String accountId, int days) {
        // Get historical portfolio values and calculate returns
        return accountRepository.getHistoricalValues(accountId, 
                Instant.now().minus(Duration.ofDays(days)));
    }

    // Risk calculation methods
    private BigDecimal calculateOverallRiskScore(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate weighted risk score based on multiple factors
        BigDecimal weightedRiskScore = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    
                    // Get individual security risk metrics
                    BigDecimal beta = marketDataService.getBeta(holding.getSymbol());
                    BigDecimal volatility = marketDataService.getVolatility(holding.getSymbol());
                    
                    // Risk score formula: (Beta * 30) + (Volatility * 70)
                    BigDecimal securityRisk = beta.multiply(BigDecimal.valueOf(30))
                            .add(volatility.multiply(BigDecimal.valueOf(70)));
                    
                    return weight.multiply(securityRisk);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Adjust for concentration risk
        BigDecimal concentrationAdjustment = calculateConcentrationRiskAdjustment(holdings);
        
        // Final risk score (0-10 scale)
        return weightedRiskScore.add(concentrationAdjustment)
                .min(BigDecimal.valueOf(10))
                .max(BigDecimal.ZERO);
    }
    private VolatilityAnalysis calculateVolatilityAnalysis(List<PerformanceDataPoint> returns) {
        if (returns.isEmpty()) {
            return VolatilityAnalysis.builder().build();
        }
        
        // Calculate daily returns
        List<BigDecimal> dailyReturns = new ArrayList<>();
        for (int i = 1; i < returns.size(); i++) {
            BigDecimal previousValue = returns.get(i - 1).getValue();
            BigDecimal currentValue = returns.get(i).getValue();
            
            if (previousValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = currentValue.subtract(previousValue)
                        .divide(previousValue, 6, RoundingMode.HALF_UP);
                dailyReturns.add(dailyReturn);
            }
        }
        
        if (dailyReturns.isEmpty()) {
            return VolatilityAnalysis.builder().build();
        }
        
        // Calculate mean return
        BigDecimal meanReturn = dailyReturns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), 6, RoundingMode.HALF_UP);
        
        // Calculate variance
        BigDecimal variance = dailyReturns.stream()
                .map(ret -> ret.subtract(meanReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size() - 1), 6, RoundingMode.HALF_UP);
        
        // Calculate standard deviation (volatility) with high precision
        BigDecimal dailyVolatility = BigDecimalMath.sqrt(variance);
        
        // Annualize volatility (sqrt(252) trading days) with high precision
        BigDecimal annualizationFactor = BigDecimalMath.sqrt(new BigDecimal("252"));
        BigDecimal annualizedVolatility = dailyVolatility.multiply(annualizationFactor, BigDecimalMath.FINANCIAL_PRECISION);
        
        // Calculate upside and downside volatility
        List<BigDecimal> upsideReturns = dailyReturns.stream()
                .filter(ret -> ret.compareTo(meanReturn) > 0)
                .collect(Collectors.toList());
        
        List<BigDecimal> downsideReturns = dailyReturns.stream()
                .filter(ret -> ret.compareTo(meanReturn) < 0)
                .collect(Collectors.toList());
        
        BigDecimal upsideVolatility = calculateVolatility(upsideReturns, meanReturn);
        BigDecimal downsideVolatility = calculateVolatility(downsideReturns, meanReturn);
        
        return VolatilityAnalysis.builder()
                .dailyVolatility(dailyVolatility)
                .annualizedVolatility(annualizedVolatility)
                .upsideVolatility(upsideVolatility)
                .downsideVolatility(downsideVolatility)
                .volatilityTrend(calculateVolatilityTrend(returns))
                .relativeToBenchmark(BigDecimal.valueOf(1.2)) // Would compare to market benchmark
                .build();
    }
    private BigDecimal calculateExpectedShortfall(List<InvestmentHolding> holdings, List<PerformanceDataPoint> returns) {
        if (returns.isEmpty() || holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Calculate daily returns
        List<BigDecimal> dailyReturns = calculateDailyReturns(returns);
        
        if (dailyReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Sort returns in ascending order (worst first)
        List<BigDecimal> sortedReturns = dailyReturns.stream()
                .sorted()
                .collect(Collectors.toList());
        
        // Calculate 95% Expected Shortfall (average of worst 5% returns)
        int cutoffIndex = (int) Math.ceil(sortedReturns.size() * 0.05);
        
        BigDecimal expectedShortfall = sortedReturns.stream()
                .limit(cutoffIndex)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(cutoffIndex), 6, RoundingMode.HALF_UP);
        
        // Convert to portfolio value terms
        BigDecimal portfolioValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return portfolioValue.multiply(expectedShortfall).abs();
    }
    private BigDecimal calculateMaxDrawdown(List<PerformanceDataPoint> returns) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = returns.get(0).getValue();
        
        for (PerformanceDataPoint point : returns) {
            BigDecimal currentValue = point.getValue();
            
            // Update peak if current value is higher
            if (currentValue.compareTo(peak) > 0) {
                peak = currentValue;
            }
            
            // Calculate drawdown from peak
            BigDecimal drawdown = peak.subtract(currentValue)
                    .divide(peak, 6, RoundingMode.HALF_UP);
            
            // Update max drawdown if current is worse
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        
        return maxDrawdown;
    }
    private BigDecimal calculatePortfolioBeta(List<InvestmentHolding> holdings) { return BigDecimal.ONE; }
    private BigDecimal calculateSharpeRatio(List<PerformanceDataPoint> returns) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        List<BigDecimal> dailyReturns = calculateDailyReturns(returns);
        
        if (dailyReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Calculate mean return
        BigDecimal meanReturn = dailyReturns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), 6, RoundingMode.HALF_UP);
        
        // Calculate standard deviation
        BigDecimal variance = dailyReturns.stream()
                .map(ret -> ret.subtract(meanReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size() - 1), 6, RoundingMode.HALF_UP);
        
        BigDecimal standardDeviation = BigDecimalMath.sqrt(variance);
        
        if (standardDeviation.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Risk-free rate (assume 2% annually = 0.02/252 daily)
        BigDecimal riskFreeRate = BigDecimal.valueOf(0.02 / 252);
        
        // Sharpe Ratio = (Mean Return - Risk Free Rate) / Standard Deviation
        BigDecimal excessReturn = meanReturn.subtract(riskFreeRate);
        BigDecimal sharpeRatio = excessReturn.divide(standardDeviation, 4, RoundingMode.HALF_UP);
        
        // Annualize the Sharpe ratio with high precision
        BigDecimal annualizationFactor = BigDecimalMath.sqrt(new BigDecimal("252"));
        return sharpeRatio.multiply(annualizationFactor, BigDecimalMath.FINANCIAL_PRECISION);
    }
    private BigDecimal calculateSortinoRatio(List<PerformanceDataPoint> returns) { return BigDecimal.ZERO; }
    private BigDecimal calculateCalmarRatio(List<PerformanceDataPoint> returns) { return BigDecimal.ZERO; }
    private Map<String, BigDecimal> calculateRiskContribution(List<InvestmentHolding> holdings) { return new HashMap<>(); }
    private BigDecimal calculateConcentrationRisk(List<InvestmentHolding> holdings) { return BigDecimal.ZERO; }
    private BigDecimal calculateLiquidityRisk(List<InvestmentHolding> holdings) { return BigDecimal.ZERO; }
    private BigDecimal calculateCurrencyRisk(List<InvestmentHolding> holdings) { return BigDecimal.ZERO; }
    private BigDecimal calculateSectorRisk(List<InvestmentHolding> holdings) { return BigDecimal.ZERO; }
    private List<String> generateRiskRecommendations(List<InvestmentHolding> holdings) {
        List<String> recommendations = new ArrayList<>();
        
        if (holdings.isEmpty()) {
            recommendations.add("Portfolio is empty. Consider adding diversified holdings.");
            return recommendations;
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Check concentration risk
        BigDecimal largestHoldingWeight = holdings.stream()
                .map(holding -> holding.getMarketValue().divide(totalValue, 4, RoundingMode.HALF_UP))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        if (largestHoldingWeight.compareTo(BigDecimal.valueOf(0.20)) > 0) {
            recommendations.add("Consider reducing concentration risk - largest holding exceeds 20% of portfolio.");
        }
        
        // Check sector diversification
        Map<String, BigDecimal> sectorAllocation = calculateSectorAllocation(holdings);
        sectorAllocation.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.valueOf(25)) > 0)
                .forEach(entry -> recommendations.add(
                    String.format("Consider reducing %s sector allocation (currently %.1f%%).", 
                        entry.getKey(), entry.getValue().doubleValue())));
        
        // Check number of holdings
        if (holdings.size() < 10) {
            recommendations.add("Consider increasing diversification with additional holdings (currently " + 
                holdings.size() + " holdings).");
        } else if (holdings.size() > 50) {
            recommendations.add("Portfolio may be over-diversified. Consider consolidating similar positions.");
        }
        
        // Check high-risk holdings
        holdings.stream()
                .filter(holding -> {
                    BigDecimal beta = marketDataService.getBeta(holding.getSymbol());
                    return beta.compareTo(BigDecimal.valueOf(1.5)) > 0;
                })
                .limit(5)
                .forEach(holding -> recommendations.add(
                    String.format("Consider the high beta (%.2f) of %s - may increase portfolio volatility.",
                        marketDataService.getBeta(holding.getSymbol()).doubleValue(),
                        holding.getSymbol())));
        
        // Check for correlation clustering
        if (hasHighCorrelationClustering(holdings)) {
            recommendations.add("Several holdings show high correlation. Consider diversifying into uncorrelated assets.");
        }
        
        // Portfolio beta recommendation
        BigDecimal portfolioBeta = calculatePortfolioBeta(holdings);
        if (portfolioBeta.compareTo(BigDecimal.valueOf(1.3)) > 0) {
            recommendations.add("Portfolio beta is high (" + portfolioBeta + "). Consider adding defensive positions.");
        } else if (portfolioBeta.compareTo(BigDecimal.valueOf(0.7)) < 0) {
            recommendations.add("Portfolio beta is low (" + portfolioBeta + "). Consider adding growth positions for higher returns.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Portfolio risk profile appears well-balanced.");
        }
        
        return recommendations;
    }
    
    // Additional placeholder methods would be implemented here...
    private List<RebalancingRecommendation> generateRebalancingRecommendations(List<InvestmentHolding> holdings, InvestmentAccount account) {
        List<RebalancingRecommendation> recommendations = new ArrayList<>();
        
        if (holdings.isEmpty() || account.getTargetAllocation() == null) {
            return recommendations;
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate current allocations
        Map<String, BigDecimal> currentAllocation = calculateAssetClassAllocation(holdings);
        Map<String, BigDecimal> targetAllocation = account.getTargetAllocation();
        
        // Compare current vs target allocations
        for (Map.Entry<String, BigDecimal> target : targetAllocation.entrySet()) {
            String assetClass = target.getKey();
            BigDecimal targetPercent = target.getValue();
            BigDecimal currentPercent = currentAllocation.getOrDefault(assetClass, BigDecimal.ZERO);
            
            BigDecimal difference = currentPercent.subtract(targetPercent);
            
            // Recommend rebalancing if difference > 5%
            if (difference.abs().compareTo(BigDecimal.valueOf(5)) > 0) {
                BigDecimal targetValue = totalValue.multiply(targetPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal currentValue = totalValue.multiply(currentPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal rebalanceAmount = targetValue.subtract(currentValue);
                
                RebalancingRecommendation recommendation = RebalancingRecommendation.builder()
                        .assetClass(assetClass)
                        .currentAllocation(currentPercent)
                        .targetAllocation(targetPercent)
                        .recommendedAction(rebalanceAmount.compareTo(BigDecimal.ZERO) > 0 ? "BUY" : "SELL")
                        .amount(rebalanceAmount.abs())
                        .priority(calculateRebalancingPriority(difference.abs()))
                        .reason(String.format("Current allocation (%.1f%%) deviates from target (%.1f%%) by %.1f%%",
                                currentPercent.doubleValue(), targetPercent.doubleValue(), difference.abs().doubleValue()))
                        .build();
                
                recommendations.add(recommendation);
            }
        }
        
        // Sort by priority (highest deviation first)
        recommendations.sort(Comparator.comparing(RebalancingRecommendation::getPriority).reversed());
        
        return recommendations;
    }
    private List<TaxOptimizationSuggestion> generateTaxOptimizationSuggestions(List<InvestmentHolding> holdings) {
        List<TaxOptimizationSuggestion> suggestions = new ArrayList<>();
        
        LocalDate now = LocalDate.now();
        LocalDate yearEnd = LocalDate.of(now.getYear(), 12, 31);
        
        for (InvestmentHolding holding : holdings) {
            BigDecimal unrealizedGainLoss = holding.getCurrentValue().subtract(holding.getCostBasis());
            BigDecimal gainLossPercent = holding.getCostBasis().compareTo(BigDecimal.ZERO) > 0 ?
                unrealizedGainLoss.divide(holding.getCostBasis(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
            
            // Tax-loss harvesting opportunity
            if (unrealizedGainLoss.compareTo(BigDecimal.valueOf(-1000)) < 0) {
                long daysHeld = Duration.between(
                    holding.getPurchaseDate().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    Instant.now()
                ).toDays();
                
                String taxRateType = daysHeld >= 365 ? "long-term" : "short-term";
                BigDecimal potentialSavings = unrealizedGainLoss.abs()
                    .multiply(daysHeld >= 365 ? BigDecimal.valueOf(0.20) : BigDecimal.valueOf(0.37));
                
                suggestions.add(TaxOptimizationSuggestion.builder()
                    .type("TAX_LOSS_HARVESTING")
                    .symbol(holding.getSymbol())
                    .description(String.format(
                        "Consider harvesting tax loss on %s (%.2f%% loss, $%,.2f unrealized loss)",
                        holding.getSymbol(), gainLossPercent, unrealizedGainLoss.abs()))
                    .potentialSavings(potentialSavings)
                    .urgency(now.isAfter(yearEnd.minusDays(30)) ? "HIGH" : "MEDIUM")
                    .actionRequired(String.format(
                        "Sell position and wait 31 days to avoid wash sale rule. Consider similar replacement security."))
                    .estimatedImpact(potentialSavings.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP).toString() + "K tax savings")
                    .build());
            }
            
            // Long-term capital gains opportunity
            long daysHeld = Duration.between(
                holding.getPurchaseDate().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                Instant.now()
            ).toDays();
            
            if (daysHeld >= 350 && daysHeld < 365 && unrealizedGainLoss.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal savingsIfWait = unrealizedGainLoss
                    .multiply(BigDecimal.valueOf(0.37 - 0.20));
                
                suggestions.add(TaxOptimizationSuggestion.builder()
                    .type("WAIT_FOR_LTCG")
                    .symbol(holding.getSymbol())
                    .description(String.format(
                        "Hold %s for %d more days to qualify for long-term capital gains rate",
                        holding.getSymbol(), 365 - daysHeld))
                    .potentialSavings(savingsIfWait)
                    .urgency("MEDIUM")
                    .actionRequired(String.format(
                        "Defer sale until %s to reduce tax rate from 37%% to 20%%",
                        holding.getPurchaseDate().plusDays(365)))
                    .estimatedImpact(savingsIfWait.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP).toString() + "K savings")
                    .build());
            }
        }
        
        // Sort by potential savings
        suggestions.sort(Comparator.comparing(TaxOptimizationSuggestion::getPotentialSavings).reversed());
        
        return suggestions;
    }
    private List<CostReductionOpportunity> identifyCostReductionOpportunities(List<InvestmentHolding> holdings) {
        List<CostReductionOpportunity> opportunities = new ArrayList<>();
        
        Map<String, List<InvestmentHolding>> byAssetClass = holdings.stream()
            .collect(Collectors.groupingBy(h -> h.getAssetClass() != null ? h.getAssetClass() : "UNKNOWN"));
        
        for (InvestmentHolding holding : holdings) {
            // High expense ratio opportunity
            BigDecimal expenseRatio = holding.getExpenseRatio() != null ? holding.getExpenseRatio() : BigDecimal.ZERO;
            if (expenseRatio.compareTo(BigDecimal.valueOf(0.50)) > 0) {
                BigDecimal annualCost = holding.getCurrentValue().multiply(expenseRatio).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal potentialSavings = annualCost.multiply(BigDecimal.valueOf(0.70)); // Assume 70% reduction with ETF
                
                opportunities.add(CostReductionOpportunity.builder()
                    .type("HIGH_EXPENSE_RATIO")
                    .symbol(holding.getSymbol())
                    .currentCost(annualCost)
                    .potentialSavings(potentialSavings)
                    .description(String.format(
                        "%s has expense ratio of %.2f%%. Consider low-cost ETF alternative.",
                        holding.getSymbol(), expenseRatio))
                    .recommendation(String.format(
                        "Replace with low-cost %s ETF (typical ER: 0.03-0.10%%)",
                        holding.getAssetClass()))
                    .priority(annualCost.compareTo(BigDecimal.valueOf(500)) > 0 ? "HIGH" : "MEDIUM")
                    .build());
            }
            
            // Overlapping holdings opportunity
            String assetClass = holding.getAssetClass() != null ? holding.getAssetClass() : "UNKNOWN";
            List<InvestmentHolding> similar = byAssetClass.get(assetClass);
            if (similar != null && similar.size() > 3) {
                BigDecimal totalValue = similar.stream()
                    .map(InvestmentHolding::getCurrentValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal avgExpenseRatio = similar.stream()
                    .map(h -> h.getExpenseRatio() != null ? h.getExpenseRatio() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(similar.size()), 4, RoundingMode.HALF_UP);
                
                if (avgExpenseRatio.compareTo(BigDecimal.valueOf(0.25)) > 0) {
                    BigDecimal consolidationSavings = totalValue
                        .multiply(avgExpenseRatio.subtract(BigDecimal.valueOf(0.10)))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    
                    opportunities.add(CostReductionOpportunity.builder()
                        .type("CONSOLIDATION")
                        .symbol(assetClass + " Holdings")
                        .currentCost(totalValue.multiply(avgExpenseRatio).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                        .potentialSavings(consolidationSavings)
                        .description(String.format(
                            "You have %d %s holdings with average ER of %.2f%%. Consolidation recommended.",
                            similar.size(), assetClass, avgExpenseRatio))
                        .recommendation(String.format(
                            "Consolidate into 1-2 diversified %s funds to reduce overlap and fees",
                            assetClass))
                        .priority("MEDIUM")
                        .build());
                    break; // Only suggest once per asset class
                }
            }
        }
        
        // Sort by potential savings
        opportunities.sort(Comparator.comparing(CostReductionOpportunity::getPotentialSavings).reversed());
        
        return opportunities;
    }
    private List<DiversificationImprovement> suggestDiversificationImprovements(List<InvestmentHolding> holdings) {
        List<DiversificationImprovement> improvements = new ArrayList<>();
        
        BigDecimal totalValue = holdings.stream()
            .map(InvestmentHolding::getCurrentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Check sector concentration
        Map<String, BigDecimal> sectorAllocation = holdings.stream()
            .collect(Collectors.groupingBy(
                h -> h.getSector() != null ? h.getSector() : "UNKNOWN",
                Collectors.reducing(BigDecimal.ZERO, InvestmentHolding::getCurrentValue, BigDecimal::add)
            ));
        
        for (Map.Entry<String, BigDecimal> entry : sectorAllocation.entrySet()) {
            BigDecimal percentage = entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if (percentage.compareTo(BigDecimal.valueOf(25)) > 0) {
                improvements.add(DiversificationImprovement.builder()
                    .type("SECTOR_CONCENTRATION")
                    .currentConcentration(percentage)
                    .recommendedMax(BigDecimal.valueOf(20))
                    .category(entry.getKey())
                    .description(String.format(
                        "%s sector represents %.1f%% of portfolio (recommended max: 20%%)",
                        entry.getKey(), percentage))
                    .recommendation(String.format(
                        "Reduce %s exposure by %.1f%% and reallocate to underweight sectors",
                        entry.getKey(), percentage.subtract(BigDecimal.valueOf(20))))
                    .riskReduction("Reduces sector-specific risk and improves portfolio stability")
                    .priority(percentage.compareTo(BigDecimal.valueOf(35)) > 0 ? "HIGH" : "MEDIUM")
                    .build());
            }
        }
        
        // Check geographic concentration
        Map<String, BigDecimal> geoAllocation = holdings.stream()
            .collect(Collectors.groupingBy(
                h -> h.getCountry() != null ? h.getCountry() : "US",
                Collectors.reducing(BigDecimal.ZERO, InvestmentHolding::getCurrentValue, BigDecimal::add)
            ));
        
        BigDecimal domesticPercentage = geoAllocation.getOrDefault("US", BigDecimal.ZERO)
            .divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        
        if (domesticPercentage.compareTo(BigDecimal.valueOf(80)) > 0) {
            improvements.add(DiversificationImprovement.builder()
                .type("GEOGRAPHIC_CONCENTRATION")
                .currentConcentration(domesticPercentage)
                .recommendedMax(BigDecimal.valueOf(70))
                .category("United States")
                .description(String.format(
                    "Portfolio is %.1f%% concentrated in US markets (recommended: 60-70%%)",
                    domesticPercentage))
                .recommendation(String.format(
                    "Add %.1f%% international exposure through developed and emerging market funds",
                    domesticPercentage.subtract(BigDecimal.valueOf(65))))
                .riskReduction("Diversifies against US-specific economic and political risks")
                .priority("MEDIUM")
                .build());
        }
        
        // Check single position concentration
        for (InvestmentHolding holding : holdings) {
            BigDecimal percentage = holding.getCurrentValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if (percentage.compareTo(BigDecimal.valueOf(15)) > 0) {
                improvements.add(DiversificationImprovement.builder()
                    .type("SINGLE_POSITION_CONCENTRATION")
                    .currentConcentration(percentage)
                    .recommendedMax(BigDecimal.valueOf(10))
                    .category(holding.getSymbol())
                    .description(String.format(
                        "%s represents %.1f%% of portfolio (recommended max: 10%% per position)",
                        holding.getSymbol(), percentage))
                    .recommendation(String.format(
                        "Trim %s position by %.1f%% to reduce single-name risk",
                        holding.getSymbol(), percentage.subtract(BigDecimal.valueOf(10))))
                    .riskReduction("Reduces idiosyncratic risk from individual company performance")
                    .priority(percentage.compareTo(BigDecimal.valueOf(20)) > 0 ? "HIGH" : "MEDIUM")
                    .build());
            }
        }
        
        // Sort by current concentration
        improvements.sort(Comparator.comparing(DiversificationImprovement::getCurrentConcentration).reversed());
        
        return improvements;
    }
    private List<RiskAdjustment> suggestRiskAdjustments(List<InvestmentHolding> holdings, InvestmentAccount account) {
        List<RiskAdjustment> adjustments = new ArrayList<>();
        
        // Calculate current portfolio volatility (simplified using holding volatilities)
        BigDecimal avgVolatility = holdings.stream()
            .map(h -> h.getVolatility() != null ? h.getVolatility() : BigDecimal.valueOf(15))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(holdings.size()), 2, RoundingMode.HALF_UP);
        
        // Get target risk level from account (defaults)
        String riskTolerance = account.getRiskTolerance() != null ? account.getRiskTolerance() : "MODERATE";
        BigDecimal targetVolatility = switch (riskTolerance) {
            case "CONSERVATIVE" -> BigDecimal.valueOf(8);
            case "MODERATE" -> BigDecimal.valueOf(12);
            case "AGGRESSIVE" -> BigDecimal.valueOf(18);
            default -> BigDecimal.valueOf(12);
        };
        
        // Check if portfolio risk exceeds target
        if (avgVolatility.compareTo(targetVolatility.multiply(BigDecimal.valueOf(1.2))) > 0) {
            BigDecimal excessVolatility = avgVolatility.subtract(targetVolatility);
            
            adjustments.add(RiskAdjustment.builder()
                .type("REDUCE_VOLATILITY")
                .currentLevel(avgVolatility)
                .targetLevel(targetVolatility)
                .deviation(excessVolatility)
                .recommendation(String.format(
                    "Portfolio volatility (%.1f%%) exceeds %s target (%.1f%%). Add bonds/fixed income.",
                    avgVolatility, riskTolerance.toLowerCase(), targetVolatility))
                .suggestedAllocation(Map.of(
                    "Increase Bonds", targetVolatility.equals(BigDecimal.valueOf(8)) ? "40-50%" : "20-30%",
                    "Reduce Equities", targetVolatility.equals(BigDecimal.valueOf(8)) ? "10-20%" : "10-15%"
                ))
                .priority(excessVolatility.compareTo(BigDecimal.valueOf(5)) > 0 ? "HIGH" : "MEDIUM")
                .build());
        }
        
        // Check for insufficient diversification (too few holdings)
        if (holdings.size() < 10) {
            adjustments.add(RiskAdjustment.builder()
                .type("INCREASE_DIVERSIFICATION")
                .currentLevel(BigDecimal.valueOf(holdings.size()))
                .targetLevel(BigDecimal.valueOf(15))
                .deviation(BigDecimal.valueOf(15 - holdings.size()))
                .recommendation(String.format(
                    "Portfolio has only %d holdings. Increase to 15-20 for better diversification.",
                    holdings.size()))
                .suggestedAllocation(Map.of(
                    "Add Diversified ETFs", "Consider total market index funds",
                    "Increase Sectors", "Add exposure to underrepresented sectors"
                ))
                .priority(holdings.size() < 5 ? "HIGH" : "MEDIUM")
                .build());
        }
        
        // Check age-based risk adjustment
        if (account.getCustomerAge() != null) {
            int age = account.getCustomerAge();
            BigDecimal recommendedEquityPercent = BigDecimal.valueOf(Math.max(20, 110 - age));
            
            BigDecimal currentEquityPercent = holdings.stream()
                .filter(h -> "EQUITY".equals(h.getAssetClass()))
                .map(InvestmentHolding::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(holdings.stream()
                    .map(InvestmentHolding::getCurrentValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                    4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            BigDecimal deviation = currentEquityPercent.subtract(recommendedEquityPercent).abs();
            
            if (deviation.compareTo(BigDecimal.valueOf(15)) > 0) {
                adjustments.add(RiskAdjustment.builder()
                    .type("AGE_BASED_ADJUSTMENT")
                    .currentLevel(currentEquityPercent)
                    .targetLevel(recommendedEquityPercent)
                    .deviation(deviation)
                    .recommendation(String.format(
                        "At age %d, target equity allocation should be around %.0f%% (currently %.1f%%)",
                        age, recommendedEquityPercent, currentEquityPercent))
                    .suggestedAllocation(Map.of(
                        currentEquityPercent.compareTo(recommendedEquityPercent) > 0 ? "Reduce Equities" : "Increase Equities",
                        String.format("%.1f%%", deviation),
                        currentEquityPercent.compareTo(recommendedEquityPercent) > 0 ? "Increase Bonds" : "Reduce Bonds",
                        String.format("%.1f%%", deviation)
                    ))
                    .priority("MEDIUM")
                    .build());
            }
        }
        
        return adjustments;
    }
    private AssetAllocationOptimization optimizeAssetAllocation(List<InvestmentHolding> holdings, InvestmentAccount account) {
        String riskTolerance = account.getRiskTolerance() != null ? account.getRiskTolerance() : "MODERATE";
        
        // Define optimal asset allocation based on risk tolerance
        Map<String, BigDecimal> optimalAllocation = switch (riskTolerance) {
            case "CONSERVATIVE" -> Map.of(
                "EQUITY", BigDecimal.valueOf(30),
                "BOND", BigDecimal.valueOf(55),
                "CASH", BigDecimal.valueOf(10),
                "ALTERNATIVE", BigDecimal.valueOf(5)
            );
            case "MODERATE" -> Map.of(
                "EQUITY", BigDecimal.valueOf(60),
                "BOND", BigDecimal.valueOf(30),
                "CASH", BigDecimal.valueOf(5),
                "ALTERNATIVE", BigDecimal.valueOf(5)
            );
            case "AGGRESSIVE" -> Map.of(
                "EQUITY", BigDecimal.valueOf(85),
                "BOND", BigDecimal.valueOf(10),
                "CASH", BigDecimal.valueOf(0),
                "ALTERNATIVE", BigDecimal.valueOf(5)
            );
            default -> Map.of(
                "EQUITY", BigDecimal.valueOf(60),
                "BOND", BigDecimal.valueOf(30),
                "CASH", BigDecimal.valueOf(5),
                "ALTERNATIVE", BigDecimal.valueOf(5)
            );
        };
        
        // Calculate current allocation
        BigDecimal totalValue = holdings.stream()
            .map(InvestmentHolding::getCurrentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, BigDecimal> currentAllocation = holdings.stream()
            .collect(Collectors.groupingBy(
                h -> h.getAssetClass() != null ? h.getAssetClass() : "OTHER",
                Collectors.reducing(BigDecimal.ZERO, InvestmentHolding::getCurrentValue, BigDecimal::add)
            )).entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            ));
        
        // Calculate adjustments needed
        Map<String, BigDecimal> adjustments = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : optimalAllocation.entrySet()) {
            BigDecimal current = currentAllocation.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal target = entry.getValue();
            adjustments.put(entry.getKey(), target.subtract(current));
        }
        
        return AssetAllocationOptimization.builder()
            .currentAllocation(currentAllocation)
            .optimalAllocation(optimalAllocation)
            .adjustments(adjustments)
            .riskTolerance(riskTolerance)
            .rebalancingPriority(adjustments.values().stream()
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(BigDecimal.valueOf(10)) > 0 ? "HIGH" : "LOW")
            .build();
    }
    private BigDecimal calculatePortfolioEfficiencyScore(List<InvestmentHolding> holdings) {
        // Portfolio Efficiency Score: Composite metric (0-100) measuring overall portfolio health
        BigDecimal score = BigDecimal.valueOf(100);
        
        BigDecimal totalValue = holdings.stream()
            .map(InvestmentHolding::getCurrentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Factor 1: Diversification (-20 if < 10 holdings)
        if (holdings.size() < 10) {
            score = score.subtract(BigDecimal.valueOf(20));
        } else if (holdings.size() < 5) {
            score = score.subtract(BigDecimal.valueOf(35));
        }
        
        // Factor 2: Cost efficiency (-15 if avg expense ratio > 0.50%)
        BigDecimal avgExpenseRatio = holdings.stream()
            .map(h -> h.getExpenseRatio() != null ? h.getExpenseRatio() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(holdings.size()), 4, RoundingMode.HALF_UP);
        
        if (avgExpenseRatio.compareTo(BigDecimal.valueOf(0.50)) > 0) {
            score = score.subtract(BigDecimal.valueOf(15));
        } else if (avgExpenseRatio.compareTo(BigDecimal.valueOf(1.0)) > 0) {
            score = score.subtract(BigDecimal.valueOf(25));
        }
        
        // Factor 3: Concentration risk (-20 if any position > 20%)
        for (InvestmentHolding holding : holdings) {
            BigDecimal percentage = holding.getCurrentValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if (percentage.compareTo(BigDecimal.valueOf(20)) > 0) {
                score = score.subtract(BigDecimal.valueOf(20));
                break;
            }
        }
        
        // Factor 4: Performance (+10 if positive overall return)
        BigDecimal totalGainLoss = holdings.stream()
            .map(h -> h.getCurrentValue().subtract(h.getCostBasis()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalGainLoss.compareTo(BigDecimal.ZERO) > 0) {
            score = score.add(BigDecimal.valueOf(10));
        }
        
        // Factor 5: Tax efficiency (+5 if long-term holdings)
        long longTermCount = holdings.stream()
            .filter(h -> Duration.between(
                h.getPurchaseDate().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                Instant.now()
            ).toDays() >= 365)
            .count();
        
        if (longTermCount >= holdings.size() * 0.6) {
            score = score.add(BigDecimal.valueOf(5));
        }
        
        // Ensure score is between 0 and 100
        return score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }
    private List<String> prioritizeRecommendations(List<InvestmentHolding> holdings) {
        List<String> priorities = new ArrayList<>();
        
        BigDecimal totalValue = holdings.stream()
            .map(InvestmentHolding::getCurrentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Priority 1: High concentration risk (>20% in single position)
        for (InvestmentHolding holding : holdings) {
            BigDecimal percentage = holding.getCurrentValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if (percentage.compareTo(BigDecimal.valueOf(20)) > 0) {
                priorities.add(String.format("HIGH: Reduce %s concentration (%.1f%% of portfolio)", holding.getSymbol(), percentage));
            }
        }
        
        // Priority 2: High expense ratios (>1.0%)
        for (InvestmentHolding holding : holdings) {
            BigDecimal expenseRatio = holding.getExpenseRatio() != null ? holding.getExpenseRatio() : BigDecimal.ZERO;
            if (expenseRatio.compareTo(BigDecimal.ONE) > 0) {
                BigDecimal annualCost = holding.getCurrentValue().multiply(expenseRatio).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                priorities.add(String.format("HIGH: Replace high-cost %s (ER: %.2f%%, Annual cost: $%,.2f)", 
                    holding.getSymbol(), expenseRatio, annualCost));
            }
        }
        
        // Priority 3: Tax-loss harvesting opportunities before year-end
        LocalDate now = LocalDate.now();
        if (now.getMonthValue() >= 11) {
            for (InvestmentHolding holding : holdings) {
                BigDecimal unrealizedLoss = holding.getCostBasis().subtract(holding.getCurrentValue());
                if (unrealizedLoss.compareTo(BigDecimal.valueOf(1000)) > 0) {
                    priorities.add(String.format("MEDIUM: Harvest tax loss on %s ($%,.2f unrealized loss) before Dec 31", 
                        holding.getSymbol(), unrealizedLoss));
                }
            }
        }
        
        // Priority 4: Rebalancing if significantly out of target
        Map<String, BigDecimal> assetClassAllocation = holdings.stream()
            .collect(Collectors.groupingBy(
                h -> h.getAssetClass() != null ? h.getAssetClass() : "OTHER",
                Collectors.reducing(BigDecimal.ZERO, InvestmentHolding::getCurrentValue, BigDecimal::add)
            ));
        
        for (Map.Entry<String, BigDecimal> entry : assetClassAllocation.entrySet()) {
            BigDecimal percentage = entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if ("EQUITY".equals(entry.getKey()) && (percentage.compareTo(BigDecimal.valueOf(75)) > 0 || percentage.compareTo(BigDecimal.valueOf(40)) < 0)) {
                priorities.add(String.format("MEDIUM: Rebalance equity allocation (currently %.1f%%, target: 55-65%%)", percentage));
            }
        }
        
        // Priority 5: Insufficient diversification
        if (holdings.size() < 8) {
            priorities.add(String.format("MEDIUM: Increase diversification (only %d holdings)", holdings.size()));
        }
        
        return priorities;
    }
    private Map<String, BigDecimal> estimateRecommendationImpact(List<InvestmentHolding> holdings) {
        Map<String, BigDecimal> impact = new HashMap<>();
        
        BigDecimal totalValue = holdings.stream()
            .map(InvestmentHolding::getCurrentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Impact 1: Cost reduction from expense ratio optimization
        BigDecimal avgExpenseRatio = holdings.stream()
            .map(h -> h.getExpenseRatio() != null ? h.getExpenseRatio() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(holdings.size()), 4, RoundingMode.HALF_UP);
        
        if (avgExpenseRatio.compareTo(BigDecimal.valueOf(0.50)) > 0) {
            BigDecimal annualSavings = totalValue
                .multiply(avgExpenseRatio.subtract(BigDecimal.valueOf(0.10)))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            impact.put("cost_reduction_annual", annualSavings);
            impact.put("cost_reduction_10yr", annualSavings.multiply(BigDecimal.valueOf(10)));
        } else {
            impact.put("cost_reduction_annual", BigDecimal.ZERO);
            impact.put("cost_reduction_10yr", BigDecimal.ZERO);
        }
        
        // Impact 2: Risk reduction from diversification
        if (holdings.size() < 10) {
            impact.put("risk_reduction_percent", BigDecimal.valueOf(15 - holdings.size()));
        } else {
            impact.put("risk_reduction_percent", BigDecimal.ZERO);
        }
        
        // Impact 3: Tax optimization potential
        BigDecimal unrealizedLosses = holdings.stream()
            .map(h -> h.getCostBasis().subtract(h.getCurrentValue()))
            .filter(loss -> loss.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal taxSavings = unrealizedLosses.multiply(BigDecimal.valueOf(0.25)); // Assume 25% effective tax rate
        impact.put("tax_loss_harvesting_potential", taxSavings);
        
        // Impact 4: Expected return improvement from rebalancing
        // Assume 0.5-1.5% annual improvement from proper allocation
        impact.put("rebalancing_return_improvement_percent", BigDecimal.valueOf(1.0));
        impact.put("rebalancing_return_improvement_annual", 
            totalValue.multiply(BigDecimal.valueOf(0.01)));
        
        // Impact 5: Total estimated annual benefit
        BigDecimal totalAnnualBenefit = impact.getOrDefault("cost_reduction_annual", BigDecimal.ZERO)
            .add(impact.getOrDefault("tax_loss_harvesting_potential", BigDecimal.ZERO))
            .add(impact.getOrDefault("rebalancing_return_improvement_annual", BigDecimal.ZERO));
        
        impact.put("total_estimated_annual_benefit", totalAnnualBenefit);
        
        return impact;
    }
    
    // Benchmark analysis methods
    private BigDecimal calculateAlpha(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        if (portfolio.isEmpty() || benchmark.isEmpty() || portfolio.size() != benchmark.size()) {
            return BigDecimal.ZERO;
        }
        
        // Alpha = Portfolio Return - (Risk-Free Rate + Beta * (Benchmark Return - Risk-Free Rate))
        BigDecimal riskFreeRate = BigDecimal.valueOf(0.04); // 4% annual risk-free rate
        BigDecimal beta = calculateBeta(portfolio, benchmark);
        
        BigDecimal portfolioReturn = portfolio.get(portfolio.size() - 1).getReturn();
        BigDecimal benchmarkReturn = benchmark.get(benchmark.size() - 1).getReturn();
        
        BigDecimal expectedReturn = riskFreeRate.add(
            beta.multiply(benchmarkReturn.subtract(riskFreeRate))
        );
        
        return portfolioReturn.subtract(expectedReturn);
    }
    
    private BigDecimal calculateBeta(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        if (portfolio.isEmpty() || benchmark.isEmpty() || portfolio.size() != benchmark.size()) {
            return BigDecimal.ONE;
        }
        
        // Beta = Covariance(Portfolio, Benchmark) / Variance(Benchmark)
        int n = portfolio.size();
        
        // Calculate means
        BigDecimal portfolioMean = portfolio.stream()
            .map(PerformanceDataPoint::getReturn)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        
        BigDecimal benchmarkMean = benchmark.stream()
            .map(PerformanceDataPoint::getReturn)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        
        // Calculate covariance and variance
        BigDecimal covariance = BigDecimal.ZERO;
        BigDecimal benchmarkVariance = BigDecimal.ZERO;
        
        for (int i = 0; i < n; i++) {
            BigDecimal portfolioDev = portfolio.get(i).getReturn().subtract(portfolioMean);
            BigDecimal benchmarkDev = benchmark.get(i).getReturn().subtract(benchmarkMean);
            
            covariance = covariance.add(portfolioDev.multiply(benchmarkDev));
            benchmarkVariance = benchmarkVariance.add(benchmarkDev.multiply(benchmarkDev));
        }
        
        covariance = covariance.divide(BigDecimal.valueOf(n - 1), 6, RoundingMode.HALF_UP);
        benchmarkVariance = benchmarkVariance.divide(BigDecimal.valueOf(n - 1), 6, RoundingMode.HALF_UP);
        
        if (benchmarkVariance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        
        return covariance.divide(benchmarkVariance, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateRSquared(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        if (portfolio.isEmpty() || benchmark.isEmpty() || portfolio.size() != benchmark.size()) {
            return BigDecimal.ZERO;
        }
        
        // R-Squared measures how much of portfolio's movements are explained by benchmark
        // R² = (Correlation)²
        int n = portfolio.size();
        
        BigDecimal portfolioMean = portfolio.stream()
            .map(PerformanceDataPoint::getReturn)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        
        BigDecimal benchmarkMean = benchmark.stream()
            .map(PerformanceDataPoint::getReturn)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        
        BigDecimal covariance = BigDecimal.ZERO;
        BigDecimal portfolioVariance = BigDecimal.ZERO;
        BigDecimal benchmarkVariance = BigDecimal.ZERO;
        
        for (int i = 0; i < n; i++) {
            BigDecimal portfolioDev = portfolio.get(i).getReturn().subtract(portfolioMean);
            BigDecimal benchmarkDev = benchmark.get(i).getReturn().subtract(benchmarkMean);
            
            covariance = covariance.add(portfolioDev.multiply(benchmarkDev));
            portfolioVariance = portfolioVariance.add(portfolioDev.multiply(portfolioDev));
            benchmarkVariance = benchmarkVariance.add(benchmarkDev.multiply(benchmarkDev));
        }
        
        if (portfolioVariance.compareTo(BigDecimal.ZERO) == 0 || benchmarkVariance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Correlation = Covariance / (StdDev_Portfolio * StdDev_Benchmark)
        BigDecimal correlation = covariance.divide(
            portfolioVariance.sqrt(new java.math.MathContext(6)).multiply(benchmarkVariance.sqrt(new java.math.MathContext(6))),
            6, RoundingMode.HALF_UP
        );
        
        // R² = Correlation²
        return correlation.multiply(correlation).setScale(4, RoundingMode.HALF_UP);
    }
    private BigDecimal calculateTrackingError(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        if (portfolio.isEmpty() || benchmark.isEmpty() || portfolio.size() != benchmark.size()) {
            return BigDecimal.ZERO;
        }
        
        // Tracking Error = Standard Deviation of (Portfolio Return - Benchmark Return)
        int n = portfolio.size();
        List<BigDecimal> returnDifferences = new ArrayList<>();
        
        for (int i = 0; i < n; i++) {
            BigDecimal diff = portfolio.get(i).getReturn().subtract(benchmark.get(i).getReturn());
            returnDifferences.add(diff);
        }
        
        BigDecimal mean = returnDifferences.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        
        BigDecimal variance = returnDifferences.stream()
            .map(diff -> diff.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(n - 1), 6, RoundingMode.HALF_UP);
        
        return variance.sqrt(new java.math.MathContext(4));
    }
    
    private BigDecimal calculateInformationRatio(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        if (portfolio.isEmpty() || benchmark.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Information Ratio = (Portfolio Return - Benchmark Return) / Tracking Error
        BigDecimal portfolioReturn = portfolio.get(portfolio.size() - 1).getReturn();
        BigDecimal benchmarkReturn = benchmark.get(benchmark.size() - 1).getReturn();
        BigDecimal excessReturn = portfolioReturn.subtract(benchmarkReturn);
        
        BigDecimal trackingError = calculateTrackingError(portfolio, benchmark);
        
        if (trackingError.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return excessReturn.divide(trackingError, 4, RoundingMode.HALF_UP);
    }
    private BigDecimal calculateUpCaptureRatio(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        if (portfolio.isEmpty() || benchmark.isEmpty() || portfolio.size() != benchmark.size()) {
            return BigDecimal.valueOf(100);
        }
        
        // Up Capture Ratio = Portfolio Up Return / Benchmark Up Return * 100
        // Measures how well portfolio captures benchmark's positive returns
        BigDecimal portfolioUpReturn = BigDecimal.ZERO;
        BigDecimal benchmarkUpReturn = BigDecimal.ZERO;
        int upPeriods = 0;
        
        for (int i = 1; i < portfolio.size(); i++) {
            BigDecimal benchmarkChange = benchmark.get(i).getReturn().subtract(benchmark.get(i-1).getReturn());
            
            if (benchmarkChange.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal portfolioChange = portfolio.get(i).getReturn().subtract(portfolio.get(i-1).getReturn());
                portfolioUpReturn = portfolioUpReturn.add(portfolioChange);
                benchmarkUpReturn = benchmarkUpReturn.add(benchmarkChange);
                upPeriods++;
            }
        }
        
        if (benchmarkUpReturn.compareTo(BigDecimal.ZERO) == 0 || upPeriods == 0) {
            return BigDecimal.valueOf(100);
        }
        
        return portfolioUpReturn.divide(benchmarkUpReturn, 2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
    
    private BigDecimal calculateDownCaptureRatio(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        if (portfolio.isEmpty() || benchmark.isEmpty() || portfolio.size() != benchmark.size()) {
            return BigDecimal.valueOf(100);
        }
        
        // Down Capture Ratio = Portfolio Down Return / Benchmark Down Return * 100
        // Lower is better - measures downside protection
        BigDecimal portfolioDownReturn = BigDecimal.ZERO;
        BigDecimal benchmarkDownReturn = BigDecimal.ZERO;
        int downPeriods = 0;
        
        for (int i = 1; i < portfolio.size(); i++) {
            BigDecimal benchmarkChange = benchmark.get(i).getReturn().subtract(benchmark.get(i-1).getReturn());
            
            if (benchmarkChange.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal portfolioChange = portfolio.get(i).getReturn().subtract(portfolio.get(i-1).getReturn());
                portfolioDownReturn = portfolioDownReturn.add(portfolioChange);
                benchmarkDownReturn = benchmarkDownReturn.add(benchmarkChange);
                downPeriods++;
            }
        }
        
        if (benchmarkDownReturn.compareTo(BigDecimal.ZERO) == 0 || downPeriods == 0) {
            return BigDecimal.valueOf(100);
        }
        
        return portfolioDownReturn.divide(benchmarkDownReturn, 2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
    private List<OutperformancePeriod> identifyOutperformancePeriods(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        List<OutperformancePeriod> periods = new ArrayList<>();
        
        if (portfolio.isEmpty() || benchmark.isEmpty() || portfolio.size() != benchmark.size()) {
            return periods;
        }
        
        LocalDate periodStart = null;
        BigDecimal cumulativeOutperformance = BigDecimal.ZERO;
        
        for (int i = 1; i < portfolio.size(); i++) {
            BigDecimal portfolioChange = portfolio.get(i).getReturn().subtract(portfolio.get(i-1).getReturn());
            BigDecimal benchmarkChange = benchmark.get(i).getReturn().subtract(benchmark.get(i-1).getReturn());
            BigDecimal dailyOutperformance = portfolioChange.subtract(benchmarkChange);
            
            if (dailyOutperformance.compareTo(BigDecimal.ZERO) > 0) {
                if (periodStart == null) {
                    periodStart = portfolio.get(i).getDate();
                    cumulativeOutperformance = dailyOutperformance;
                } else {
                    cumulativeOutperformance = cumulativeOutperformance.add(dailyOutperformance);
                }
            } else if (periodStart != null) {
                // End of outperformance period
                LocalDate periodEnd = portfolio.get(i-1).getDate();
                
                if (cumulativeOutperformance.compareTo(BigDecimal.valueOf(0.5)) > 0) {
                    periods.add(OutperformancePeriod.builder()
                        .startDate(periodStart)
                        .endDate(periodEnd)
                        .outperformance(cumulativeOutperformance)
                        .duration(java.time.Period.between(periodStart, periodEnd).getDays())
                        .build());
                }
                
                periodStart = null;
                cumulativeOutperformance = BigDecimal.ZERO;
            }
        }
        
        // Handle ongoing outperformance period
        if (periodStart != null && cumulativeOutperformance.compareTo(BigDecimal.valueOf(0.5)) > 0) {
            periods.add(OutperformancePeriod.builder()
                .startDate(periodStart)
                .endDate(portfolio.get(portfolio.size()-1).getDate())
                .outperformance(cumulativeOutperformance)
                .duration(java.time.Period.between(periodStart, portfolio.get(portfolio.size()-1).getDate()).getDays())
                .build());
        }
        
        // Sort by outperformance magnitude
        periods.sort(Comparator.comparing(OutperformancePeriod::getOutperformance).reversed());
        
        return periods;
    }
    private AttributionAnalysis performAttributionAnalysis(List<PerformanceDataPoint> portfolio, List<PerformanceDataPoint> benchmark) {
        if (portfolio.isEmpty() || benchmark.isEmpty()) {
            return AttributionAnalysis.builder()
                .allocationEffect(BigDecimal.ZERO)
                .selectionEffect(BigDecimal.ZERO)
                .interactionEffect(BigDecimal.ZERO)
                .totalExcess(BigDecimal.ZERO)
                .build();
        }
        
        // Brinson Attribution Analysis: Total Excess = Allocation + Selection + Interaction
        BigDecimal portfolioReturn = portfolio.get(portfolio.size() - 1).getReturn();
        BigDecimal benchmarkReturn = benchmark.get(benchmark.size() - 1).getReturn();
        BigDecimal totalExcess = portfolioReturn.subtract(benchmarkReturn);
        
        // For simplified attribution without detailed sector data:
        // Allocation Effect = Estimated contribution from overweight/underweight positions
        // Selection Effect = Estimated contribution from security selection
        BigDecimal beta = calculateBeta(portfolio, benchmark);
        
        // Allocation effect: (Beta - 1) * Benchmark Return
        BigDecimal allocationEffect = beta.subtract(BigDecimal.ONE).multiply(benchmarkReturn);
        
        // Selection effect: Alpha (captures stock selection skill)
        BigDecimal selectionEffect = calculateAlpha(portfolio, benchmark);
        
        // Interaction effect: Residual
        BigDecimal interactionEffect = totalExcess.subtract(allocationEffect).subtract(selectionEffect);
        
        return AttributionAnalysis.builder()
            .allocationEffect(allocationEffect)
            .selectionEffect(selectionEffect)
            .interactionEffect(interactionEffect)
            .totalExcess(totalExcess)
            .analysisNote(String.format(
                "Allocation %s: %.2f%%, Selection %s: %.2f%%, Interaction: %.2f%%",
                allocationEffect.compareTo(BigDecimal.ZERO) > 0 ? "added" : "detracted",
                allocationEffect,
                selectionEffect.compareTo(BigDecimal.ZERO) > 0 ? "added" : "detracted",
                selectionEffect,
                interactionEffect
            ))
            .build();
    }
    
    // Additional helper methods
    private BigDecimal calculateConcentrationRiskAdjustment(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate Herfindahl-Hirschman Index
        BigDecimal hhi = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    return weight.multiply(weight);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Convert HHI to risk adjustment (0-2 scale)
        return hhi.multiply(BigDecimal.valueOf(2));
    }
    
    private List<BigDecimal> calculateDailyReturns(List<PerformanceDataPoint> returns) {
        List<BigDecimal> dailyReturns = new ArrayList<>();
        
        for (int i = 1; i < returns.size(); i++) {
            BigDecimal previousValue = returns.get(i - 1).getValue();
            BigDecimal currentValue = returns.get(i).getValue();
            
            if (previousValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = currentValue.subtract(previousValue)
                        .divide(previousValue, 6, RoundingMode.HALF_UP);
                dailyReturns.add(dailyReturn);
            }
        }
        
        return dailyReturns;
    }
    
    private BigDecimal calculateVolatility(List<BigDecimal> returns, BigDecimal mean) {
        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal variance = returns.stream()
                .map(ret -> ret.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        
        return BigDecimalMath.sqrt(variance);
    }
    
    private String calculateVolatilityTrend(List<PerformanceDataPoint> returns) {
        if (returns.size() < 60) {
            return "INSUFFICIENT_DATA";
        }
        
        // Compare last 30 days vs previous 30 days
        List<PerformanceDataPoint> recent = returns.subList(0, 30);
        List<PerformanceDataPoint> previous = returns.subList(30, 60);
        
        BigDecimal recentVol = calculatePortfolioVolatilityFromReturns(recent);
        BigDecimal previousVol = calculatePortfolioVolatilityFromReturns(previous);
        
        if (recentVol.compareTo(previousVol.multiply(BigDecimal.valueOf(1.1))) > 0) {
            return "INCREASING";
        } else if (recentVol.compareTo(previousVol.multiply(BigDecimal.valueOf(0.9))) < 0) {
            return "DECREASING";
        } else {
            return "STABLE";
        }
    }
    
    private BigDecimal calculatePortfolioVolatilityFromReturns(List<PerformanceDataPoint> returns) {
        List<BigDecimal> dailyReturns = calculateDailyReturns(returns);
        
        if (dailyReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal mean = dailyReturns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), 6, RoundingMode.HALF_UP);
        
        return calculateVolatility(dailyReturns, mean);
    }
    
    private boolean hasHighCorrelationClustering(List<InvestmentHolding> holdings) {
        if (holdings.size() < 3) {
            return false;
        }
        
        int highCorrelationPairs = 0;
        int totalPairs = 0;
        
        for (int i = 0; i < holdings.size(); i++) {
            for (int j = i + 1; j < holdings.size(); j++) {
                BigDecimal correlation = marketDataService.getCorrelation(
                    holdings.get(i).getSymbol(), 
                    holdings.get(j).getSymbol(), 
                    252
                );
                
                totalPairs++;
                if (correlation.compareTo(BigDecimal.valueOf(0.7)) > 0) {
                    highCorrelationPairs++;
                }
            }
        }
        
        // If more than 30% of pairs have high correlation
        return (double) highCorrelationPairs / totalPairs > 0.3;
    }
    
    private String calculateRebalancingPriority(BigDecimal deviation) {
        if (deviation.compareTo(BigDecimal.valueOf(15)) > 0) {
            return "HIGH";
        } else if (deviation.compareTo(BigDecimal.valueOf(10)) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    // Additional calculation methods that were referenced
    private Map<String, BigDecimal> calculateSectorWeights(List<InvestmentHolding> holdings) {
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return holdings.stream()
                .collect(Collectors.groupingBy(
                        holding -> marketDataService.getSector(holding.getSymbol()),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                InvestmentHolding::getMarketValue,
                                BigDecimal::add
                        )
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP)
                ));
    }
    
    private BigDecimal calculateLargestHoldingWeight(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return holdings.stream()
                .map(holding -> holding.getMarketValue().divide(totalValue, 4, RoundingMode.HALF_UP))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }
    
    private BigDecimal calculateTop5HoldingsWeight(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .sorted(Comparator.reverseOrder())
                .limit(5)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(totalValue, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateDiversificationRatio(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Simplified diversification ratio calculation
        // In production, would use full covariance matrix
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal weightedVolatility = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    BigDecimal volatility = marketDataService.getVolatility(holding.getSymbol());
                    return weight.multiply(volatility);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal portfolioVolatility = calculatePortfolioVolatility(holdings);
        
        if (portfolioVolatility.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return weightedVolatility.divide(portfolioVolatility, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateConcentrationScore(List<InvestmentHolding> holdings) {
        if (holdings.isEmpty()) {
            return BigDecimal.valueOf(100);
        }
        
        // Calculate entropy-based concentration score
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal entropy = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    if (weight.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                    }
                    // -weight * ln(weight) with high precision
                    BigDecimal lnWeight = BigDecimalMath.ln(weight);
                    return weight.multiply(lnWeight, BigDecimalMath.FINANCIAL_PRECISION).negate();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Normalize to 0-100 scale (higher = less concentrated)
        BigDecimal maxEntropy = BigDecimalMath.ln(new BigDecimal(holdings.size()));
        BigDecimal normalizedEntropy = maxEntropy.compareTo(BigDecimal.ZERO) > 0 ? 
            entropy.divide(maxEntropy, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : 
            BigDecimal.ZERO;
        
        return BigDecimal.valueOf(100).subtract(normalizedEntropy); // Invert so higher = more concentrated
    }
    
    private BigDecimal calculateES(List<InvestmentHolding> holdings, double confidenceLevel) {
        // Simplified Expected Shortfall calculation
        return calculateVaR(holdings, confidenceLevel).multiply(BigDecimal.valueOf(1.3));
    }
    
    private BigDecimal calculateCVaR(List<InvestmentHolding> holdings, double confidenceLevel) {
        // Conditional Value at Risk - same as Expected Shortfall for this implementation
        return calculateES(holdings, confidenceLevel);
    }
    
    private BigDecimal calculateAllocationEffect(List<InvestmentHolding> holdings) {
        // Simplified allocation effect calculation
        // Would need benchmark weights in production
        return BigDecimal.valueOf(0.5);
    }
    
    private BigDecimal calculateSelectionEffect(List<InvestmentHolding> holdings) {
        // Simplified selection effect calculation
        // Would need benchmark returns in production
        return BigDecimal.valueOf(1.2);
    }
    
    private BigDecimal calculateInteractionEffect(List<InvestmentHolding> holdings) {
        // Simplified interaction effect calculation
        return BigDecimal.valueOf(0.1);
    }
    
    private BigDecimal calculateScenarioImpact(List<InvestmentHolding> holdings, String scenario, BigDecimal shockSize) {
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal impactValue = holdings.stream()
                .map(holding -> {
                    BigDecimal beta = marketDataService.getBeta(holding.getSymbol());
                    BigDecimal sectorSensitivity = getSectorSensitivity(
                        marketDataService.getSector(holding.getSymbol()), scenario);
                    
                    BigDecimal holdingImpact = shockSize.multiply(beta).multiply(sectorSensitivity);
                    return holding.getMarketValue().multiply(holdingImpact);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return impactValue.divide(totalValue, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal getSectorSensitivity(String sector, String scenario) {
        // Sector sensitivity to different scenarios
        Map<String, Map<String, BigDecimal>> sectorSensitivities = Map.of(
            "Technology", Map.of(
                "MARKET_CRASH", BigDecimal.valueOf(1.2),
                "INTEREST_RATE_SHOCK", BigDecimal.valueOf(1.1),
                "CURRENCY_CRISIS", BigDecimal.valueOf(0.8)
            ),
            "Financial", Map.of(
                "MARKET_CRASH", BigDecimal.valueOf(1.5),
                "INTEREST_RATE_SHOCK", BigDecimal.valueOf(0.7),
                "CURRENCY_CRISIS", BigDecimal.valueOf(1.1)
            )
        );
        
        return sectorSensitivities.getOrDefault(sector, Map.of())
                .getOrDefault(scenario, BigDecimal.ONE);
    }
    
    private BigDecimal calculatePortfolioResilience(Map<String, BigDecimal> scenarios) {
        // Calculate resilience score based on scenario performance
        BigDecimal averageImpact = scenarios.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scenarios.size()), 4, RoundingMode.HALF_UP);
        
        // Resilience score (0-100, higher is better)
        return BigDecimal.valueOf(100).add(averageImpact.multiply(BigDecimal.valueOf(100)))
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100));
    }
    
    private Map<String, BigDecimal> calculateSectorESGScores(List<InvestmentHolding> holdings) {
        return holdings.stream()
                .collect(Collectors.groupingBy(
                        holding -> marketDataService.getSector(holding.getSymbol()),
                        Collectors.averagingDouble(holding -> 
                            marketDataService.getESGScore(holding.getSymbol()).doubleValue())
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> BigDecimal.valueOf(entry.getValue())
                ));
    }
    
    private Map<String, BigDecimal> calculateESGMetrics(List<InvestmentHolding> holdings) {
        // Calculate weighted ESG component scores
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, BigDecimal> metrics = new HashMap<>();
        
        // Environmental score
        BigDecimal envScore = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    BigDecimal score = marketDataService.getEnvironmentalScore(holding.getSymbol());
                    return weight.multiply(score);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.put("Environmental", envScore);
        
        // Social score
        BigDecimal socialScore = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    BigDecimal score = marketDataService.getSocialScore(holding.getSymbol());
                    return weight.multiply(score);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.put("Social", socialScore);
        
        // Governance score
        BigDecimal govScore = holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    BigDecimal score = marketDataService.getGovernanceScore(holding.getSymbol());
                    return weight.multiply(score);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.put("Governance", govScore);
        
        return metrics;
    }
    
    private String calculateSustainabilityRating(BigDecimal esgScore) {
        if (esgScore.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "EXCELLENT";
        } else if (esgScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "GOOD";
        } else if (esgScore.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }
    
    private String calculateControversyLevel(List<InvestmentHolding> holdings) {
        long controversialHoldings = holdings.stream()
                .mapToLong(holding -> marketDataService.hasControversies(holding.getSymbol()) ? 1 : 0)
                .sum();
        
        double controversyPercentage = (double) controversialHoldings / holdings.size() * 100;
        
        if (controversyPercentage > 20) {
            return "HIGH";
        } else if (controversyPercentage > 10) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    private BigDecimal calculateCarbonFootprint(List<InvestmentHolding> holdings) {
        BigDecimal totalValue = holdings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return holdings.stream()
                .map(holding -> {
                    BigDecimal weight = holding.getMarketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    BigDecimal carbonIntensity = marketDataService.getCarbonIntensity(holding.getSymbol());
                    return weight.multiply(carbonIntensity);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateOverallSentimentScore(Map<String, SentimentMetrics> holdingSentiment) {
        if (holdingSentiment.isEmpty()) {
            return BigDecimal.valueOf(50); // Neutral
        }
        
        return holdingSentiment.values().stream()
                .map(SentimentMetrics::getOverallScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(holdingSentiment.size()), 2, RoundingMode.HALF_UP);
    }
    
    private Map<String, BigDecimal> calculateSectorSentiment(List<InvestmentHolding> holdings, 
            Map<String, SentimentMetrics> holdingSentiment) {
        return holdings.stream()
                .collect(Collectors.groupingBy(
                        holding -> marketDataService.getSector(holding.getSymbol()),
                        Collectors.averagingDouble(holding -> 
                            holdingSentiment.getOrDefault(holding.getSymbol(), 
                                SentimentMetrics.neutral()).getOverallScore().doubleValue())
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> BigDecimal.valueOf(entry.getValue())
                ));
    }
    
    private Map<String, String> analyzeSentimentTrends(Map<String, SentimentMetrics> holdingSentiment) {
        return holdingSentiment.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getTrend()
                ));
    }
    
    private BigDecimal calculateSentimentBasedRiskAdjustment(Map<String, SentimentMetrics> holdingSentiment) {
        // Calculate risk adjustment based on sentiment
        BigDecimal avgSentiment = calculateOverallSentimentScore(holdingSentiment);
        
        // If sentiment is very negative (< 30), increase risk by 20%
        if (avgSentiment.compareTo(BigDecimal.valueOf(30)) < 0) {
            return BigDecimal.valueOf(1.2);
        }
        // If sentiment is very positive (> 70), increase risk by 10% (overconfidence)
        else if (avgSentiment.compareTo(BigDecimal.valueOf(70)) > 0) {
            return BigDecimal.valueOf(1.1);
        }
        // Neutral sentiment
        else {
            return BigDecimal.ONE;
        }
    }
}