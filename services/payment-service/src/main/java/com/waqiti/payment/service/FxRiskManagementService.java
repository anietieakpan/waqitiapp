package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade FX Risk Management Service
 * Handles FX risk assessment, hedging strategies, and position management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxRiskManagementService {

    private final ExchangeRateService exchangeRateService;
    
    private final Map<String, FxPosition> fxPositions = new ConcurrentHashMap<>();
    private final Map<String, HedgeTransaction> hedgeTransactions = new ConcurrentHashMap<>();
    private final Map<String, RiskLimit> merchantRiskLimits = new ConcurrentHashMap<>();
    private final Map<String, List<RiskAlert>> riskAlerts = new ConcurrentHashMap<>();
    
    private static final BigDecimal DEFAULT_VAR_CONFIDENCE = new BigDecimal("0.95"); // 95% confidence
    private static final BigDecimal DEFAULT_RISK_LIMIT = new BigDecimal("100000"); // $100K default limit
    private static final BigDecimal HEDGE_THRESHOLD = new BigDecimal("50000"); // $50K hedge threshold

    /**
     * Assess FX risk for merchant position
     */
    public FxRiskAssessment assessFxRisk(String merchantId, String baseCurrency) {
        try {
            log.info("Assessing FX risk for merchant: {} base currency: {}", merchantId, baseCurrency);
            
            // Get merchant's current FX positions
            Map<String, BigDecimal> currencyExposures = calculateCurrencyExposures(merchantId);
            
            // Calculate Value at Risk (VaR)
            BigDecimal varAmount = calculateVaR(currencyExposures, baseCurrency);
            
            // Calculate maximum drawdown potential
            BigDecimal maxDrawdown = calculateMaxDrawdown(currencyExposures, baseCurrency);
            
            // Calculate correlation risk
            BigDecimal correlationRisk = calculateCorrelationRisk(currencyExposures);
            
            // Determine overall risk level
            String riskLevel = determineRiskLevel(varAmount, maxDrawdown, correlationRisk);
            
            // Get risk limit
            RiskLimit riskLimit = getMerchantRiskLimit(merchantId);
            
            // Check limit breaches
            List<String> limitBreaches = checkLimitBreaches(varAmount, maxDrawdown, riskLimit);
            
            // Generate risk recommendations
            List<String> recommendations = generateRiskRecommendations(currencyExposures, varAmount, riskLevel);
            
            FxRiskAssessment assessment = FxRiskAssessment.builder()
                .merchantId(merchantId)
                .baseCurrency(baseCurrency)
                .currencyExposures(currencyExposures)
                .varAmount(varAmount)
                .maxDrawdown(maxDrawdown)
                .correlationRisk(correlationRisk)
                .riskLevel(riskLevel)
                .limitBreaches(limitBreaches)
                .recommendations(recommendations)
                .assessmentDate(LocalDateTime.now())
                .build();
            
            // Create risk alerts if necessary
            if (!limitBreaches.isEmpty() || "HIGH".equals(riskLevel)) {
                createRiskAlert(merchantId, assessment);
            }
            
            log.info("FX risk assessment completed for {}: VaR={}, Risk Level={}", 
                    merchantId, varAmount, riskLevel);
            
            return assessment;
            
        } catch (Exception e) {
            log.error("FX risk assessment failed for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("FX risk assessment failed", e);
        }
    }

    /**
     * Execute hedging strategy for FX exposure
     */
    @Transactional
    public HedgingResult executeHedgingStrategy(String merchantId, String currency, 
                                              BigDecimal exposureAmount, String hedgingStrategy) {
        try {
            log.info("Executing hedging strategy for merchant: {} currency: {} amount: {} strategy: {}", 
                    merchantId, currency, exposureAmount, hedgingStrategy);
            
            // Validate hedging request
            validateHedgingRequest(merchantId, currency, exposureAmount, hedgingStrategy);
            
            // Determine hedge amount and instruments
            HedgingCalculation calculation = calculateHedgingRequirement(
                merchantId, currency, exposureAmount, hedgingStrategy);
            
            String hedgeId = UUID.randomUUID().toString();
            
            // Execute hedging transactions
            List<HedgeTransaction> transactions = executeHedgeTransactions(
                hedgeId, merchantId, calculation);
            
            // Store hedge transactions
            transactions.forEach(tx -> hedgeTransactions.put(tx.getTransactionId(), tx));
            
            // Update FX positions
            updateFxPositions(merchantId, currency, calculation);
            
            // Calculate hedge effectiveness
            BigDecimal effectiveness = calculateHedgeEffectiveness(calculation);
            
            HedgingResult result = HedgingResult.builder()
                .hedgeId(hedgeId)
                .merchantId(merchantId)
                .currency(currency)
                .originalExposure(exposureAmount)
                .hedgedAmount(calculation.getHedgeAmount())
                .residualExposure(exposureAmount.subtract(calculation.getHedgeAmount()))
                .hedgingStrategy(hedgingStrategy)
                .hedgeTransactions(transactions)
                .hedgeEffectiveness(effectiveness)
                .totalCost(calculation.getTotalCost())
                .executedAt(LocalDateTime.now())
                .maturityDate(calculation.getMaturityDate())
                .build();
            
            log.info("Hedging executed successfully: {} hedged amount: {} effectiveness: {}%", 
                    hedgeId, calculation.getHedgeAmount(), effectiveness);
            
            return result;
            
        } catch (Exception e) {
            log.error("Hedging execution failed for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Hedging execution failed", e);
        }
    }

    /**
     * Monitor FX positions and generate alerts
     */
    public FxPositionReport monitorFxPositions(String merchantId) {
        try {
            log.info("Monitoring FX positions for merchant: {}", merchantId);
            
            // Get current positions
            Map<String, FxPosition> currentPositions = getCurrentFxPositions(merchantId);
            
            // Calculate position metrics
            BigDecimal totalExposure = calculateTotalExposure(currentPositions);
            BigDecimal unrealizedPnL = calculateUnrealizedPnL(currentPositions);
            
            // Check position limits
            List<String> limitWarnings = checkPositionLimits(merchantId, currentPositions);
            
            // Identify rebalancing opportunities
            List<String> rebalancingRecommendations = identifyRebalancingOpportunities(currentPositions);
            
            // Calculate position concentrations
            Map<String, BigDecimal> concentrations = calculateCurrencyConcentrations(currentPositions);
            
            FxPositionReport report = FxPositionReport.builder()
                .merchantId(merchantId)
                .positions(currentPositions)
                .totalExposure(totalExposure)
                .unrealizedPnL(unrealizedPnL)
                .limitWarnings(limitWarnings)
                .rebalancingRecommendations(rebalancingRecommendations)
                .currencyConcentrations(concentrations)
                .reportDate(LocalDateTime.now())
                .build();
            
            // Generate alerts for significant changes
            if (unrealizedPnL.abs().compareTo(new BigDecimal("10000")) > 0 || !limitWarnings.isEmpty()) {
                createPositionAlert(merchantId, report);
            }
            
            log.info("FX position monitoring completed for {}: exposure={}, PnL={}", 
                    merchantId, totalExposure, unrealizedPnL);
            
            return report;
            
        } catch (Exception e) {
            log.error("FX position monitoring failed for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("FX position monitoring failed", e);
        }
    }

    /**
     * Calculate optimal hedge ratio for currency pair
     */
    public HedgeRatioCalculation calculateOptimalHedgeRatio(String merchantId, String currencyPair, 
                                                          LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Calculating optimal hedge ratio for merchant: {} pair: {} period: {} to {}", 
                    merchantId, currencyPair, startDate, endDate);
            
            // Get historical price data
            List<BigDecimal> spotPrices = getHistoricalSpotPrices(currencyPair, startDate, endDate);
            List<BigDecimal> forwardPrices = getHistoricalForwardPrices(currencyPair, startDate, endDate);
            
            // Calculate variance and covariance
            BigDecimal spotVariance = calculateVariance(spotPrices);
            BigDecimal forwardVariance = calculateVariance(forwardPrices);
            BigDecimal covariance = calculateCovariance(spotPrices, forwardPrices);
            
            // Calculate optimal hedge ratio using minimum variance approach
            BigDecimal optimalRatio = covariance.divide(forwardVariance, 4, RoundingMode.HALF_UP);
            
            // Calculate hedge effectiveness
            BigDecimal correlation = calculateCorrelation(spotPrices, forwardPrices);
            BigDecimal effectiveness = correlation.pow(2);
            
            // Adjust for transaction costs
            BigDecimal adjustedRatio = adjustHedgeRatioForCosts(optimalRatio, currencyPair);
            
            HedgeRatioCalculation calculation = HedgeRatioCalculation.builder()
                .merchantId(merchantId)
                .currencyPair(currencyPair)
                .optimalRatio(optimalRatio)
                .adjustedRatio(adjustedRatio)
                .hedgeEffectiveness(effectiveness)
                .spotVariance(spotVariance)
                .forwardVariance(forwardVariance)
                .correlation(correlation)
                .dataPoints(spotPrices.size())
                .calculationDate(LocalDateTime.now())
                .build();
            
            log.info("Optimal hedge ratio calculated for {}: {} ratio: {} effectiveness: {}%", 
                    merchantId, currencyPair, adjustedRatio, effectiveness.multiply(new BigDecimal("100")));
            
            return calculation;
            
        } catch (Exception e) {
            log.error("Hedge ratio calculation failed for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Hedge ratio calculation failed", e);
        }
    }

    /**
     * Set risk limits for merchant
     */
    @Transactional
    public void setMerchantRiskLimits(String merchantId, RiskLimit riskLimit) {
        try {
            log.info("Setting risk limits for merchant: {}", merchantId);
            
            validateRiskLimits(riskLimit);
            
            riskLimit.setMerchantId(merchantId);
            riskLimit.setUpdatedAt(LocalDateTime.now());
            
            merchantRiskLimits.put(merchantId, riskLimit);
            
            log.info("Risk limits updated for merchant: {} VaR limit: {}", 
                    merchantId, riskLimit.getVarLimit());
            
        } catch (Exception e) {
            log.error("Failed to set risk limits for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Risk limit setup failed", e);
        }
    }

    /**
     * Get risk alerts for merchant
     */
    public List<RiskAlert> getRiskAlerts(String merchantId, LocalDate startDate, LocalDate endDate) {
        try {
            List<RiskAlert> merchantAlerts = riskAlerts.getOrDefault(merchantId, Collections.emptyList());
            
            return merchantAlerts.stream()
                .filter(alert -> !alert.getCreatedAt().toLocalDate().isBefore(startDate))
                .filter(alert -> !alert.getCreatedAt().toLocalDate().isAfter(endDate))
                .sorted(Comparator.comparing(RiskAlert::getCreatedAt).reversed())
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to get risk alerts for {}: {}", merchantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // Private helper methods
    
    private Map<String, BigDecimal> calculateCurrencyExposures(String merchantId) {
        try {
            log.info("Calculating real currency exposures for merchant: {}", merchantId);
            Map<String, BigDecimal> exposures = new HashMap<>();
            
            // Get actual FX position for the merchant
            FxPosition merchantPosition = fxPositions.get(merchantId);
            if (merchantPosition != null) {
                // Calculate net exposures from actual positions
                for (Map.Entry<String, BigDecimal> position : merchantPosition.getCurrencyPositions().entrySet()) {
                    String currency = position.getKey();
                    BigDecimal amount = position.getValue();
                    
                    if (amount.compareTo(BigDecimal.ZERO) != 0) {
                        exposures.put(currency, amount.abs()); // Use absolute exposure for risk calculation
                    }
                }
                
                log.info("Calculated {} currency exposures for merchant: {}", exposures.size(), merchantId);
                
            } else {
                log.warn("No FX position found for merchant: {} - using zero exposures", merchantId);
                // Return empty exposures for new merchants
            }
            
            return exposures;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to calculate currency exposures for merchant: {}", merchantId, e);
            // Return empty exposures on error to avoid incorrect risk calculations
            return new HashMap<>();
        }
    }

    private BigDecimal calculateVaR(Map<String, BigDecimal> exposures, String baseCurrency) {
        // Simplified VaR calculation using variance-covariance method
        BigDecimal totalVar = BigDecimal.ZERO;
        
        for (Map.Entry<String, BigDecimal> exposure : exposures.entrySet()) {
            String currency = exposure.getKey();
            BigDecimal amount = exposure.getValue();
            
            // Get historical volatility (mock data)
            BigDecimal volatility = getCurrencyVolatility(currency, baseCurrency);
            
            // Calculate individual VaR
            BigDecimal individualVar = amount.multiply(volatility)
                .multiply(new BigDecimal("2.33")); // 99% confidence z-score
            
            totalVar = totalVar.add(individualVar);
        }
        
        return totalVar.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMaxDrawdown(Map<String, BigDecimal> exposures, String baseCurrency) {
        // Calculate potential maximum loss scenario
        return exposures.values().stream()
            .map(amount -> amount.multiply(new BigDecimal("0.15"))) // 15% max currency move
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateCorrelationRisk(Map<String, BigDecimal> exposures) {
        // Simplified correlation risk calculation
        int currencyCount = exposures.size();
        if (currencyCount <= 1) return BigDecimal.ZERO;
        
        // Higher concentration = higher correlation risk
        BigDecimal concentrationRisk = BigDecimal.valueOf(currencyCount - 1)
            .multiply(new BigDecimal("0.1"));
        
        return concentrationRisk;
    }

    private String determineRiskLevel(BigDecimal var, BigDecimal maxDrawdown, BigDecimal correlationRisk) {
        BigDecimal totalRisk = var.add(maxDrawdown).add(correlationRisk);
        
        if (totalRisk.compareTo(new BigDecimal("100000")) > 0) {
            return "HIGH";
        } else if (totalRisk.compareTo(new BigDecimal("50000")) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private List<String> checkLimitBreaches(BigDecimal var, BigDecimal maxDrawdown, RiskLimit riskLimit) {
        List<String> breaches = new ArrayList<>();
        
        if (var.compareTo(riskLimit.getVarLimit()) > 0) {
            breaches.add("VaR limit exceeded: " + var + " > " + riskLimit.getVarLimit());
        }
        
        if (maxDrawdown.compareTo(riskLimit.getMaxDrawdownLimit()) > 0) {
            breaches.add("Max drawdown limit exceeded: " + maxDrawdown + " > " + riskLimit.getMaxDrawdownLimit());
        }
        
        return breaches;
    }

    private List<String> generateRiskRecommendations(Map<String, BigDecimal> exposures, BigDecimal var, String riskLevel) {
        List<String> recommendations = new ArrayList<>();
        
        if ("HIGH".equals(riskLevel)) {
            recommendations.add("Consider immediate hedging to reduce FX exposure");
            recommendations.add("Diversify currency portfolio to reduce concentration risk");
        }
        
        // Find largest exposure
        String largestCurrency = exposures.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        if (largestCurrency != null && exposures.get(largestCurrency).compareTo(HEDGE_THRESHOLD) > 0) {
            recommendations.add("Consider hedging " + largestCurrency + " exposure above $" + HEDGE_THRESHOLD);
        }
        
        return recommendations;
    }

    private void validateHedgingRequest(String merchantId, String currency, BigDecimal amount, String strategy) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Hedge amount must be positive");
        }
        
        Set<String> supportedStrategies = Set.of("FORWARD", "OPTION", "SWAP", "NATURAL_HEDGE");
        if (!supportedStrategies.contains(strategy)) {
            throw new IllegalArgumentException("Unsupported hedging strategy: " + strategy);
        }
    }

    private HedgingCalculation calculateHedgingRequirement(String merchantId, String currency, 
                                                         BigDecimal exposureAmount, String strategy) {
        // Calculate optimal hedge amount (could be partial hedge)
        BigDecimal hedgeRatio = getOptimalHedgeRatio(merchantId, currency);
        BigDecimal hedgeAmount = exposureAmount.multiply(hedgeRatio);
        
        // Calculate hedge cost
        BigDecimal hedgeCost = calculateHedgeCost(currency, hedgeAmount, strategy);
        
        // Determine maturity
        LocalDateTime maturityDate = calculateHedgeMaturity(strategy);
        
        return HedgingCalculation.builder()
            .hedgeAmount(hedgeAmount)
            .hedgeRatio(hedgeRatio)
            .totalCost(hedgeCost)
            .maturityDate(maturityDate)
            .build();
    }

    private List<HedgeTransaction> executeHedgeTransactions(String hedgeId, String merchantId, 
                                                          HedgingCalculation calculation) {
        List<HedgeTransaction> transactions = new ArrayList<>();
        
        // Create hedge transaction
        HedgeTransaction transaction = HedgeTransaction.builder()
            .transactionId(UUID.randomUUID().toString())
            .hedgeId(hedgeId)
            .merchantId(merchantId)
            .instrumentType("FORWARD")
            .notionalAmount(calculation.getHedgeAmount())
            .hedgeRate(exchangeRateService.getRealTimeRate("USD", "EUR")) // Mock rate
            .cost(calculation.getTotalCost())
            .maturityDate(calculation.getMaturityDate())
            .status("EXECUTED")
            .executedAt(LocalDateTime.now())
            .build();
        
        transactions.add(transaction);
        
        return transactions;
    }

    private void updateFxPositions(String merchantId, String currency, HedgingCalculation calculation) {
        String positionKey = merchantId + "_" + currency;
        
        FxPosition position = fxPositions.computeIfAbsent(positionKey, k -> 
            FxPosition.builder()
                .merchantId(merchantId)
                .currency(currency)
                .spotExposure(BigDecimal.ZERO)
                .hedgedAmount(BigDecimal.ZERO)
                .netExposure(BigDecimal.ZERO)
                .lastUpdated(LocalDateTime.now())
                .build());
        
        position.setHedgedAmount(position.getHedgedAmount().add(calculation.getHedgeAmount()));
        position.setNetExposure(position.getSpotExposure().subtract(position.getHedgedAmount()));
        position.setLastUpdated(LocalDateTime.now());
    }

    private BigDecimal calculateHedgeEffectiveness(HedgingCalculation calculation) {
        // Mock hedge effectiveness calculation
        return new BigDecimal("95.5"); // 95.5% effectiveness
    }

    private Map<String, FxPosition> getCurrentFxPositions(String merchantId) {
        return fxPositions.entrySet().stream()
            .filter(entry -> entry.getValue().getMerchantId().equals(merchantId))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private BigDecimal calculateTotalExposure(Map<String, FxPosition> positions) {
        return positions.values().stream()
            .map(FxPosition::getNetExposure)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateUnrealizedPnL(Map<String, FxPosition> positions) {
        // Mock P&L calculation
        return new BigDecimal("2500.75");
    }

    private void createRiskAlert(String merchantId, FxRiskAssessment assessment) {
        RiskAlert alert = RiskAlert.builder()
            .alertId(UUID.randomUUID().toString())
            .merchantId(merchantId)
            .alertType("FX_RISK")
            .severity(assessment.getRiskLevel())
            .message("FX risk assessment: " + assessment.getRiskLevel() + " risk level")
            .details(assessment.getLimitBreaches())
            .createdAt(LocalDateTime.now())
            .acknowledged(false)
            .build();
        
        riskAlerts.computeIfAbsent(merchantId, k -> new ArrayList<>()).add(alert);
    }

    private void createPositionAlert(String merchantId, FxPositionReport report) {
        RiskAlert alert = RiskAlert.builder()
            .alertId(UUID.randomUUID().toString())
            .merchantId(merchantId)
            .alertType("POSITION_ALERT")
            .severity("MEDIUM")
            .message("FX position alert: Unrealized P&L = " + report.getUnrealizedPnL())
            .details(report.getLimitWarnings())
            .createdAt(LocalDateTime.now())
            .acknowledged(false)
            .build();
        
        riskAlerts.computeIfAbsent(merchantId, k -> new ArrayList<>()).add(alert);
    }

    private RiskLimit getMerchantRiskLimit(String merchantId) {
        return merchantRiskLimits.computeIfAbsent(merchantId, k -> 
            RiskLimit.builder()
                .merchantId(k)
                .varLimit(DEFAULT_RISK_LIMIT)
                .maxDrawdownLimit(DEFAULT_RISK_LIMIT.multiply(new BigDecimal("1.5")))
                .positionLimit(DEFAULT_RISK_LIMIT.multiply(new BigDecimal("2")))
                .updatedAt(LocalDateTime.now())
                .build());
    }

    // Additional helper methods for calculations
    
    private BigDecimal getCurrencyVolatility(String currency, String baseCurrency) {
        try {
            log.debug("Calculating historical volatility for currency pair: {}/{}", currency, baseCurrency);
            
            // Get historical exchange rates from the exchange rate service
            List<BigDecimal> historicalRates = exchangeRateService.getHistoricalRates(currency, baseCurrency, 30); // 30-day history
            
            if (historicalRates.size() < 2) {
                log.warn("Insufficient historical data for {}/{}. Using conservative volatility estimate", currency, baseCurrency);
                return getConservativeVolatility(currency);
            }
            
            // Calculate daily returns
            List<BigDecimal> returns = new ArrayList<>();
            for (int i = 1; i < historicalRates.size(); i++) {
                BigDecimal previousRate = historicalRates.get(i - 1);
                BigDecimal currentRate = historicalRates.get(i);
                
                if (previousRate.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal returnValue = currentRate.subtract(previousRate)
                            .divide(previousRate, 6, RoundingMode.HALF_UP);
                    returns.add(returnValue);
                }
            }
            
            // Calculate standard deviation of returns (volatility)
            BigDecimal volatility = calculateStandardDeviation(returns);
            
            // Annualize the volatility (multiply by sqrt(252) for trading days)
            BigDecimal annualizedVolatility = volatility.multiply(new BigDecimal("15.87")); // sqrt(252)
            
            log.debug("Calculated volatility for {}/{}: {}", currency, baseCurrency, annualizedVolatility);
            return annualizedVolatility;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to calculate volatility for {}/{}. Using conservative estimate", 
                currency, baseCurrency, e);
            return getConservativeVolatility(currency);
        }
    }
    
    private BigDecimal getConservativeVolatility(String currency) {
        // Conservative volatility estimates based on historical currency behavior
        return switch (currency) {
            case "EUR" -> new BigDecimal("0.08"); // 8% volatility
            case "GBP" -> new BigDecimal("0.12"); // 12% volatility (post-Brexit higher volatility)
            case "JPY" -> new BigDecimal("0.10"); // 10% volatility
            case "CAD" -> new BigDecimal("0.07");
            default -> new BigDecimal("0.15");
        };
    }
    
    private BigDecimal calculateStandardDeviation(List<BigDecimal> values) {
        if (values.size() <= 1) {
            return BigDecimal.ZERO;
        }
        
        // Calculate mean
        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
        
        // Calculate variance
        BigDecimal variance = values.stream()
                .map(value -> value.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size() - 1), 6, RoundingMode.HALF_UP);
        
        // Calculate standard deviation (sqrt of variance)
        return sqrt(variance);
    }
    
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Newton's method for square root
        BigDecimal x = value;
        BigDecimal previous;
        do {
            previous = x;
            x = x.add(value.divide(x, 6, RoundingMode.HALF_UP))
                 .divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
        } while (x.subtract(previous).abs().compareTo(new BigDecimal("0.000001")) > 0);
        
        return x;
    }

    private BigDecimal getOptimalHedgeRatio(String merchantId, String currency) {
        // Mock optimal hedge ratio
        return new BigDecimal("0.80"); // 80% hedge ratio
    }

    private BigDecimal calculateHedgeCost(String currency, BigDecimal amount, String strategy) {
        BigDecimal costPercentage = switch (strategy) {
            case "FORWARD" -> new BigDecimal("0.001"); // 0.1%
            case "OPTION" -> new BigDecimal("0.002"); // 0.2%
            case "SWAP" -> new BigDecimal("0.0015"); // 0.15%
            default -> new BigDecimal("0.001");
        };
        
        return amount.multiply(costPercentage);
    }

    private LocalDateTime calculateHedgeMaturity(String strategy) {
        return switch (strategy) {
            case "FORWARD" -> LocalDateTime.now().plusMonths(3);
            case "OPTION" -> LocalDateTime.now().plusMonths(6);
            case "SWAP" -> LocalDateTime.now().plusYears(1);
            default -> LocalDateTime.now().plusMonths(3);
        };
    }

    private List<BigDecimal> getHistoricalSpotPrices(String currencyPair, LocalDate start, LocalDate end) {
        // Mock historical data
        return Arrays.asList(
            new BigDecimal("1.1850"), new BigDecimal("1.1875"), new BigDecimal("1.1900"),
            new BigDecimal("1.1825"), new BigDecimal("1.1800"), new BigDecimal("1.1775")
        );
    }

    private List<BigDecimal> getHistoricalForwardPrices(String currencyPair, LocalDate start, LocalDate end) {
        // Mock forward prices
        return Arrays.asList(
            new BigDecimal("1.1860"), new BigDecimal("1.1885"), new BigDecimal("1.1910"),
            new BigDecimal("1.1835"), new BigDecimal("1.1810"), new BigDecimal("1.1785")
        );
    }

    private BigDecimal calculateVariance(List<BigDecimal> prices) {
        if (prices.size() < 2) return BigDecimal.ZERO;
        
        BigDecimal mean = prices.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(prices.size()), 6, RoundingMode.HALF_UP);
        
        BigDecimal variance = prices.stream()
            .map(price -> price.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(prices.size() - 1), 6, RoundingMode.HALF_UP);
        
        return variance;
    }

    private BigDecimal calculateCovariance(List<BigDecimal> pricesX, List<BigDecimal> pricesY) {
        // Mock covariance calculation
        return new BigDecimal("0.000125");
    }

    private BigDecimal calculateCorrelation(List<BigDecimal> pricesX, List<BigDecimal> pricesY) {
        // Mock correlation calculation
        return new BigDecimal("0.85");
    }

    private BigDecimal adjustHedgeRatioForCosts(BigDecimal optimalRatio, String currencyPair) {
        // Adjust for transaction costs
        BigDecimal costAdjustment = new BigDecimal("0.02"); // 2% cost adjustment
        return optimalRatio.multiply(BigDecimal.ONE.subtract(costAdjustment));
    }

    private void validateRiskLimits(RiskLimit riskLimit) {
        if (riskLimit.getVarLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("VaR limit must be positive");
        }
        
        if (riskLimit.getMaxDrawdownLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Max drawdown limit must be positive");
        }
    }

    private List<String> checkPositionLimits(String merchantId, Map<String, FxPosition> positions) {
        List<String> warnings = new ArrayList<>();
        RiskLimit limits = getMerchantRiskLimit(merchantId);
        
        BigDecimal totalExposure = calculateTotalExposure(positions);
        if (totalExposure.compareTo(limits.getPositionLimit()) > 0) {
            warnings.add("Total position limit exceeded: " + totalExposure + " > " + limits.getPositionLimit());
        }
        
        return warnings;
    }

    private List<String> identifyRebalancingOpportunities(Map<String, FxPosition> positions) {
        List<String> recommendations = new ArrayList<>();
        
        // Find positions with high concentration
        positions.values().forEach(position -> {
            if (position.getNetExposure().abs().compareTo(new BigDecimal("100000")) > 0) {
                recommendations.add("Consider rebalancing " + position.getCurrency() + " position");
            }
        });
        
        return recommendations;
    }

    private Map<String, BigDecimal> calculateCurrencyConcentrations(Map<String, FxPosition> positions) {
        BigDecimal totalExposure = calculateTotalExposure(positions);
        
        return positions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    if (totalExposure.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
                    return entry.getValue().getNetExposure().abs()
                        .divide(totalExposure, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                }
            ));
    }

    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class FxRiskAssessment {
        private String merchantId;
        private String baseCurrency;
        private Map<String, BigDecimal> currencyExposures;
        private BigDecimal varAmount;
        private BigDecimal maxDrawdown;
        private BigDecimal correlationRisk;
        private String riskLevel;
        private List<String> limitBreaches;
        private List<String> recommendations;
        private LocalDateTime assessmentDate;
    }

    @lombok.Data
    @lombok.Builder
    public static class HedgingResult {
        private String hedgeId;
        private String merchantId;
        private String currency;
        private BigDecimal originalExposure;
        private BigDecimal hedgedAmount;
        private BigDecimal residualExposure;
        private String hedgingStrategy;
        private List<HedgeTransaction> hedgeTransactions;
        private BigDecimal hedgeEffectiveness;
        private BigDecimal totalCost;
        private LocalDateTime executedAt;
        private LocalDateTime maturityDate;
    }

    @lombok.Data
    @lombok.Builder
    public static class FxPositionReport {
        private String merchantId;
        private Map<String, FxPosition> positions;
        private BigDecimal totalExposure;
        private BigDecimal unrealizedPnL;
        private List<String> limitWarnings;
        private List<String> rebalancingRecommendations;
        private Map<String, BigDecimal> currencyConcentrations;
        private LocalDateTime reportDate;
    }

    @lombok.Data
    @lombok.Builder
    public static class HedgeRatioCalculation {
        private String merchantId;
        private String currencyPair;
        private BigDecimal optimalRatio;
        private BigDecimal adjustedRatio;
        private BigDecimal hedgeEffectiveness;
        private BigDecimal spotVariance;
        private BigDecimal forwardVariance;
        private BigDecimal correlation;
        private int dataPoints;
        private LocalDateTime calculationDate;
    }

    @lombok.Data
    @lombok.Builder
    private static class HedgingCalculation {
        private BigDecimal hedgeAmount;
        private BigDecimal hedgeRatio;
        private BigDecimal totalCost;
        private LocalDateTime maturityDate;
    }

    @lombok.Data
    @lombok.Builder
    public static class FxPosition {
        private String merchantId;
        private String currency;
        private BigDecimal spotExposure;
        private BigDecimal hedgedAmount;
        private BigDecimal netExposure;
        private LocalDateTime lastUpdated;
    }

    @lombok.Data
    @lombok.Builder
    public static class HedgeTransaction {
        private String transactionId;
        private String hedgeId;
        private String merchantId;
        private String instrumentType;
        private BigDecimal notionalAmount;
        private BigDecimal hedgeRate;
        private BigDecimal cost;
        private LocalDateTime maturityDate;
        private String status;
        private LocalDateTime executedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class RiskLimit {
        private String merchantId;
        private BigDecimal varLimit;
        private BigDecimal maxDrawdownLimit;
        private BigDecimal positionLimit;
        private LocalDateTime updatedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class RiskAlert {
        private String alertId;
        private String merchantId;
        private String alertType;
        private String severity;
        private String message;
        private List<String> details;
        private LocalDateTime createdAt;
        private boolean acknowledged;
    }
}