package com.waqiti.ml.service;

import com.waqiti.ml.dto.TransactionData;
import com.waqiti.ml.entity.UserBehaviorProfile;
import com.waqiti.ml.entity.TransactionPattern;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Production-ready Statistical Analysis Service for advanced fraud detection.
 * Provides statistical anomaly detection, pattern analysis, and behavioral analytics.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticalAnalysisService {

    @Value("${statistics.z.score.threshold:3.0}")
    private double zScoreThreshold;

    @Value("${statistics.iqr.multiplier:1.5}")
    private double iqrMultiplier;

    @Value("${statistics.confidence.level:0.95}")
    private double confidenceLevel;

    @Value("${statistics.min.sample.size:30}")
    private int minSampleSize;

    @Value("${statistics.seasonality.detection:true}")
    private boolean seasonalityDetectionEnabled;

    @Value("${statistics.trend.analysis:true}")
    private boolean trendAnalysisEnabled;

    // Statistical models cache
    private final Map<String, UserStatisticalModel> modelCache = new ConcurrentHashMap<>();

    // Time series data cache
    private final Map<String, TimeSeriesData> timeSeriesCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("StatisticalAnalysisService initialized with z-score threshold: {}", zScoreThreshold);
    }

    /**
     * Perform comprehensive statistical analysis on transaction
     */
    @Traced(operation = "statistical_analysis")
    public StatisticalAnalysisResult analyzeTransaction(TransactionData transaction, 
                                                       UserBehaviorProfile profile,
                                                       List<TransactionPattern> historicalPatterns) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Starting statistical analysis for transaction: {}", transaction.getTransactionId());

            StatisticalAnalysisResult result = StatisticalAnalysisResult.builder()
                .transactionId(transaction.getTransactionId())
                .userId(transaction.getUserId())
                .timestamp(LocalDateTime.now())
                .build();

            // Ensure we have sufficient data
            if (historicalPatterns == null || historicalPatterns.size() < minSampleSize) {
                return createInsufficientDataResult(result);
            }

            // Perform various statistical analyses
            performOutlierDetection(result, transaction, historicalPatterns);
            performDistributionAnalysis(result, transaction, historicalPatterns);
            performTimeSeriesAnalysis(result, transaction, historicalPatterns);
            performCorrelationAnalysis(result, transaction, historicalPatterns);
            performBenfordAnalysis(result, transaction, historicalPatterns);
            performVelocityAnalysis(result, transaction, historicalPatterns);
            performBurstDetection(result, transaction, historicalPatterns);
            performSeasonalityAnalysis(result, transaction, historicalPatterns);
            
            // Calculate overall statistical anomaly score
            double anomalyScore = calculateOverallAnomalyScore(result);
            result.setOverallAnomalyScore(anomalyScore);
            result.setAnomalous(anomalyScore > 0.7);
            
            // Generate statistical insights
            generateStatisticalInsights(result);
            
            long duration = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(duration);
            
            log.debug("Statistical analysis completed in {}ms for transaction: {}, anomaly score: {}", 
                duration, transaction.getTransactionId(), anomalyScore);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in statistical analysis for transaction: {}", transaction.getTransactionId(), e);
            throw new MLProcessingException("Failed to perform statistical analysis", e);
        }
    }

    /**
     * Perform outlier detection using multiple methods
     */
    private void performOutlierDetection(StatisticalAnalysisResult result,
                                        TransactionData transaction,
                                        List<TransactionPattern> historicalPatterns) {
        
        // Extract amounts for analysis
        double[] amounts = historicalPatterns.stream()
            .mapToDouble(p -> p.getAmount().doubleValue())
            .toArray();
        
        double currentAmount = transaction.getAmount().doubleValue();
        
        // Z-Score method
        DescriptiveStatistics stats = new DescriptiveStatistics(amounts);
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        
        if (stdDev > 0) {
            double zScore = (currentAmount - mean) / stdDev;
            result.setAmountZScore(zScore);
            result.setZScoreOutlier(Math.abs(zScore) > zScoreThreshold);
        }
        
        // IQR method
        double q1 = stats.getPercentile(25);
        double q3 = stats.getPercentile(75);
        double iqr = q3 - q1;
        
        double lowerBound = q1 - (iqrMultiplier * iqr);
        double upperBound = q3 + (iqrMultiplier * iqr);
        
        result.setIqrLowerBound(lowerBound);
        result.setIqrUpperBound(upperBound);
        result.setIqrOutlier(currentAmount < lowerBound || currentAmount > upperBound);
        
        // Modified Z-Score (using median absolute deviation)
        double median = stats.getPercentile(50);
        double[] deviations = Arrays.stream(amounts)
            .map(a -> Math.abs(a - median))
            .toArray();
        
        DescriptiveStatistics madStats = new DescriptiveStatistics(deviations);
        double mad = madStats.getPercentile(50);
        
        if (mad > 0) {
            double modifiedZScore = 0.6745 * (currentAmount - median) / mad;
            result.setModifiedZScore(modifiedZScore);
            result.setModifiedZScoreOutlier(Math.abs(modifiedZScore) > 3.5);
        }
        
        // Isolation Forest score (simplified)
        double isolationScore = calculateIsolationScore(currentAmount, amounts);
        result.setIsolationForestScore(isolationScore);
        result.setIsolationForestOutlier(isolationScore > 0.6);
        
        // Local Outlier Factor (LOF) - simplified
        double lofScore = calculateLocalOutlierFactor(currentAmount, amounts);
        result.setLocalOutlierFactor(lofScore);
        result.setLofOutlier(lofScore > 2.0);
    }

    /**
     * Perform distribution analysis
     */
    private void performDistributionAnalysis(StatisticalAnalysisResult result,
                                           TransactionData transaction,
                                           List<TransactionPattern> historicalPatterns) {
        
        double[] amounts = historicalPatterns.stream()
            .mapToDouble(p -> p.getAmount().doubleValue())
            .toArray();
        
        DescriptiveStatistics stats = new DescriptiveStatistics(amounts);
        
        // Basic statistics
        result.setHistoricalMean(stats.getMean());
        result.setHistoricalMedian(stats.getPercentile(50));
        result.setHistoricalStdDev(stats.getStandardDeviation());
        result.setHistoricalVariance(stats.getVariance());
        result.setHistoricalSkewness(stats.getSkewness());
        result.setHistoricalKurtosis(stats.getKurtosis());
        
        // Percentile ranking
        double currentAmount = transaction.getAmount().doubleValue();
        double percentileRank = calculatePercentileRank(currentAmount, amounts);
        result.setPercentileRank(percentileRank);
        result.setExtremePercentile(percentileRank < 5 || percentileRank > 95);
        
        // Distribution fit test (Kolmogorov-Smirnov simplified)
        boolean isNormallyDistributed = testNormalDistribution(amounts);
        result.setNormallyDistributed(isNormallyDistributed);
        
        if (isNormallyDistributed) {
            NormalDistribution normalDist = new NormalDistribution(stats.getMean(), stats.getStandardDeviation());
            double probability = normalDist.cumulativeProbability(currentAmount);
            result.setProbabilityUnderNormal(probability);
            result.setUnlikelyUnderNormal(probability < 0.025 || probability > 0.975);
        }
        
        // Entropy calculation
        double entropy = calculateEntropy(amounts);
        result.setDistributionEntropy(entropy);
        result.setHighEntropy(entropy > 3.0);
    }

    /**
     * Perform time series analysis
     */
    private void performTimeSeriesAnalysis(StatisticalAnalysisResult result,
                                         TransactionData transaction,
                                         List<TransactionPattern> historicalPatterns) {
        
        if (!trendAnalysisEnabled) return;
        
        // Sort patterns by timestamp
        List<TransactionPattern> sortedPatterns = historicalPatterns.stream()
            .sorted(Comparator.comparing(TransactionPattern::getTimestamp))
            .collect(Collectors.toList());
        
        // Extract time series data
        double[] values = sortedPatterns.stream()
            .mapToDouble(p -> p.getAmount().doubleValue())
            .toArray();
        
        long[] timestamps = sortedPatterns.stream()
            .mapToLong(p -> p.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC))
            .toArray();
        
        // Trend analysis using linear regression
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < timestamps.length; i++) {
            regression.addData(timestamps[i], values[i]);
        }
        
        double slope = regression.getSlope();
        double intercept = regression.getIntercept();
        double rSquared = regression.getRSquare();
        
        result.setTrendSlope(slope);
        result.setTrendIntercept(intercept);
        result.setTrendRSquared(rSquared);
        result.setSignificantTrend(rSquared > 0.5 && Math.abs(slope) > 0.01);
        
        // Predict expected value
        long currentTimestamp = transaction.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC);
        double predictedValue = regression.predict(currentTimestamp);
        double actualValue = transaction.getAmount().doubleValue();
        double predictionError = Math.abs(actualValue - predictedValue);
        
        result.setPredictedValue(predictedValue);
        result.setPredictionError(predictionError);
        result.setSignificantDeviation(predictionError > 2 * regression.getMeanSquareError());
        
        // Moving average analysis
        int windowSize = Math.min(10, values.length / 3);
        double movingAverage = calculateMovingAverage(values, windowSize);
        double movingStdDev = calculateMovingStdDev(values, windowSize);
        
        result.setMovingAverage(movingAverage);
        result.setMovingStdDev(movingStdDev);
        result.setOutsideMovingRange(
            actualValue < movingAverage - 2 * movingStdDev ||
            actualValue > movingAverage + 2 * movingStdDev
        );
        
        // Autocorrelation
        double autocorrelation = calculateAutocorrelation(values, 1);
        result.setAutocorrelation(autocorrelation);
        result.setHighAutocorrelation(Math.abs(autocorrelation) > 0.7);
    }

    /**
     * Perform correlation analysis
     */
    private void performCorrelationAnalysis(StatisticalAnalysisResult result,
                                          TransactionData transaction,
                                          List<TransactionPattern> historicalPatterns) {
        
        // Analyze correlations between different features
        Map<String, Double> correlations = new HashMap<>();
        
        // Amount vs Hour correlation
        double[] amounts = historicalPatterns.stream()
            .mapToDouble(p -> p.getAmount().doubleValue())
            .toArray();
        
        double[] hours = historicalPatterns.stream()
            .mapToDouble(p -> p.getHourOfDay())
            .toArray();
        
        if (amounts.length == hours.length && amounts.length > 2) {
            PearsonsCorrelation correlation = new PearsonsCorrelation();
            double amountHourCorr = correlation.correlation(amounts, hours);
            correlations.put("amount_hour", amountHourCorr);
        }
        
        // Amount vs Day of Week correlation
        double[] daysOfWeek = historicalPatterns.stream()
            .mapToDouble(p -> p.getDayOfWeek())
            .toArray();
        
        if (amounts.length == daysOfWeek.length && amounts.length > 2) {
            PearsonsCorrelation correlation = new PearsonsCorrelation();
            double amountDayCorr = correlation.correlation(amounts, daysOfWeek);
            correlations.put("amount_day", amountDayCorr);
        }
        
        result.setFeatureCorrelations(correlations);
        
        // Check for unusual correlations
        boolean unusualCorrelation = correlations.values().stream()
            .anyMatch(corr -> Math.abs(corr) > 0.8);
        result.setUnusualCorrelation(unusualCorrelation);
    }

    /**
     * Perform Benford's Law analysis
     */
    private void performBenfordAnalysis(StatisticalAnalysisResult result,
                                       TransactionData transaction,
                                       List<TransactionPattern> historicalPatterns) {
        
        // Expected Benford distribution for first digit
        double[] benfordExpected = {0.301, 0.176, 0.125, 0.097, 0.079, 0.067, 0.058, 0.051, 0.046};
        
        // Calculate actual distribution
        int[] firstDigitCounts = new int[9];
        int totalCount = 0;
        
        for (TransactionPattern pattern : historicalPatterns) {
            String amountStr = pattern.getAmount().toString().replaceAll("[^0-9]", "");
            if (!amountStr.isEmpty()) {
                int firstDigit = Character.getNumericValue(amountStr.charAt(0));
                if (firstDigit >= 1 && firstDigit <= 9) {
                    firstDigitCounts[firstDigit - 1]++;
                    totalCount++;
                }
            }
        }
        
        // Add current transaction
        String currentAmountStr = transaction.getAmount().toString().replaceAll("[^0-9]", "");
        if (!currentAmountStr.isEmpty()) {
            int currentFirstDigit = Character.getNumericValue(currentAmountStr.charAt(0));
            if (currentFirstDigit >= 1 && currentFirstDigit <= 9) {
                firstDigitCounts[currentFirstDigit - 1]++;
                totalCount++;
            }
        }
        
        if (totalCount > 0) {
            // Calculate chi-square statistic
            double chiSquare = 0.0;
            for (int i = 0; i < 9; i++) {
                double observed = (double) firstDigitCounts[i] / totalCount;
                double expected = benfordExpected[i];
                if (expected > 0) {
                    chiSquare += Math.pow(observed - expected, 2) / expected;
                }
            }
            
            result.setBenfordChiSquare(chiSquare);
            result.setBenfordAnomaly(chiSquare > 15.51); // Chi-square critical value at 0.05 significance
            
            // Calculate current digit probability
            if (currentFirstDigit >= 1 && currentFirstDigit <= 9) {
                double expectedProb = benfordExpected[currentFirstDigit - 1];
                result.setBenfordExpectedProbability(expectedProb);
                result.setUnusualFirstDigit(expectedProb < 0.05);
            }
        }
    }

    /**
     * Perform velocity analysis
     */
    private void performVelocityAnalysis(StatisticalAnalysisResult result,
                                       TransactionData transaction,
                                       List<TransactionPattern> historicalPatterns) {
        
        // Calculate transaction velocity
        LocalDateTime now = transaction.getTimestamp();
        
        // Transactions in last hour
        long txInLastHour = historicalPatterns.stream()
            .filter(p -> ChronoUnit.HOURS.between(p.getTimestamp(), now) <= 1)
            .count();
        
        // Transactions in last day
        long txInLastDay = historicalPatterns.stream()
            .filter(p -> ChronoUnit.DAYS.between(p.getTimestamp(), now) <= 1)
            .count();
        
        result.setTransactionsInLastHour(txInLastHour);
        result.setTransactionsInLastDay(txInLastDay);
        
        // Calculate average time between transactions
        if (historicalPatterns.size() > 1) {
            List<TransactionPattern> sorted = historicalPatterns.stream()
                .sorted(Comparator.comparing(TransactionPattern::getTimestamp))
                .collect(Collectors.toList());
            
            double[] intervals = new double[sorted.size() - 1];
            for (int i = 1; i < sorted.size(); i++) {
                intervals[i - 1] = ChronoUnit.MINUTES.between(
                    sorted.get(i - 1).getTimestamp(),
                    sorted.get(i).getTimestamp()
                );
            }
            
            DescriptiveStatistics intervalStats = new DescriptiveStatistics(intervals);
            result.setAverageTimeBetweenTx(intervalStats.getMean());
            result.setStdDevTimeBetweenTx(intervalStats.getStandardDeviation());
            
            // Check if current transaction is too fast
            if (!sorted.isEmpty()) {
                long timeSinceLastTx = ChronoUnit.MINUTES.between(
                    sorted.get(sorted.size() - 1).getTimestamp(),
                    now
                );
                
                if (intervalStats.getMean() > 0 && intervalStats.getStandardDeviation() > 0) {
                    double velocityZScore = (timeSinceLastTx - intervalStats.getMean()) / 
                                          intervalStats.getStandardDeviation();
                    result.setVelocityZScore(velocityZScore);
                    result.setHighVelocity(velocityZScore < -2.0); // Much faster than usual
                }
            }
        }
        
        // Check for velocity spike
        double historicalAvgDaily = calculateHistoricalAverageDailyTransactions(historicalPatterns);
        result.setVelocitySpike(txInLastDay > historicalAvgDaily * 3);
    }

    /**
     * Perform burst detection
     */
    private void performBurstDetection(StatisticalAnalysisResult result,
                                     TransactionData transaction,
                                     List<TransactionPattern> historicalPatterns) {
        
        // Detect transaction bursts
        List<TransactionBurst> bursts = detectTransactionBursts(historicalPatterns);
        
        result.setBurstCount(bursts.size());
        
        if (!bursts.isEmpty()) {
            // Get most recent burst
            TransactionBurst recentBurst = bursts.get(bursts.size() - 1);
            result.setInBurst(isTransactionInBurst(transaction, recentBurst));
            result.setMaxBurstSize(
                bursts.stream().mapToInt(b -> b.transactionCount).max().orElse(0)
            );
            
            // Calculate burst intensity
            double burstIntensity = calculateBurstIntensity(bursts, historicalPatterns.size());
            result.setBurstIntensity(burstIntensity);
            result.setHighBurstActivity(burstIntensity > 0.3);
        }
    }

    /**
     * Perform seasonality analysis
     */
    private void performSeasonalityAnalysis(StatisticalAnalysisResult result,
                                          TransactionData transaction,
                                          List<TransactionPattern> historicalPatterns) {
        
        if (!seasonalityDetectionEnabled) return;
        
        // Hourly seasonality
        Map<Integer, Double> hourlyPattern = calculateHourlyPattern(historicalPatterns);
        int currentHour = transaction.getTimestamp().getHour();
        Double expectedHourlyRate = hourlyPattern.get(currentHour);
        
        if (expectedHourlyRate != null) {
            result.setExpectedHourlyRate(expectedHourlyRate);
            result.setUnusualHour(expectedHourlyRate < 0.05); // Less than 5% of transactions
        }
        
        // Daily seasonality
        Map<Integer, Double> dailyPattern = calculateDailyPattern(historicalPatterns);
        int currentDay = transaction.getTimestamp().getDayOfWeek().getValue();
        Double expectedDailyRate = dailyPattern.get(currentDay);
        
        if (expectedDailyRate != null) {
            result.setExpectedDailyRate(expectedDailyRate);
            result.setUnusualDay(expectedDailyRate < 0.1); // Less than 10% of transactions
        }
        
        // Monthly seasonality
        Map<Integer, Double> monthlyPattern = calculateMonthlyPattern(historicalPatterns);
        int currentDayOfMonth = transaction.getTimestamp().getDayOfMonth();
        Double expectedMonthlyRate = monthlyPattern.get(currentDayOfMonth);
        
        if (expectedMonthlyRate != null) {
            result.setExpectedMonthlyRate(expectedMonthlyRate);
            result.setUnusualDayOfMonth(expectedMonthlyRate < 0.03); // Less than 3% of transactions
        }
        
        // Check if transaction violates seasonal patterns
        boolean violatesSeasonality = 
            (expectedHourlyRate != null && expectedHourlyRate < 0.02) ||
            (expectedDailyRate != null && expectedDailyRate < 0.05);
        
        result.setViolatesSeasonality(violatesSeasonality);
    }

    /**
     * Calculate overall anomaly score
     */
    private double calculateOverallAnomalyScore(StatisticalAnalysisResult result) {
        double score = 0.0;
        int factors = 0;
        
        // Outlier detection contribution
        if (result.isZScoreOutlier()) { score += 0.8; factors++; }
        if (result.isIqrOutlier()) { score += 0.7; factors++; }
        if (result.isModifiedZScoreOutlier()) { score += 0.8; factors++; }
        if (result.isIsolationForestOutlier()) { score += 0.9; factors++; }
        if (result.isLofOutlier()) { score += 0.85; factors++; }
        
        // Distribution analysis contribution
        if (result.isExtremePercentile()) { score += 0.7; factors++; }
        if (result.isUnlikelyUnderNormal()) { score += 0.75; factors++; }
        if (result.isHighEntropy()) { score += 0.6; factors++; }
        
        // Time series contribution
        if (result.isSignificantDeviation()) { score += 0.8; factors++; }
        if (result.isOutsideMovingRange()) { score += 0.7; factors++; }
        
        // Velocity contribution
        if (result.isHighVelocity()) { score += 0.75; factors++; }
        if (result.isVelocitySpike()) { score += 0.8; factors++; }
        
        // Burst detection contribution
        if (result.isInBurst() && result.isHighBurstActivity()) { score += 0.7; factors++; }
        
        // Benford's Law contribution
        if (result.isBenfordAnomaly()) { score += 0.85; factors++; }
        
        // Seasonality contribution
        if (result.isViolatesSeasonality()) { score += 0.65; factors++; }
        
        return factors > 0 ? score / factors : 0.0;
    }

    /**
     * Generate statistical insights
     */
    private void generateStatisticalInsights(StatisticalAnalysisResult result) {
        List<String> insights = new ArrayList<>();
        
        if (result.isZScoreOutlier()) {
            insights.add(String.format("Amount is %.1f standard deviations from mean", 
                Math.abs(result.getAmountZScore())));
        }
        
        if (result.isExtremePercentile()) {
            insights.add(String.format("Amount is in the %.1f percentile", 
                result.getPercentileRank()));
        }
        
        if (result.isSignificantDeviation()) {
            insights.add(String.format("Amount deviates %.1f from predicted value", 
                result.getPredictionError()));
        }
        
        if (result.isHighVelocity()) {
            insights.add("Transaction velocity is unusually high");
        }
        
        if (result.isViolatesSeasonality()) {
            insights.add("Transaction violates normal seasonal patterns");
        }
        
        if (result.isBenfordAnomaly()) {
            insights.add("Amount distribution violates Benford's Law");
        }
        
        result.setStatisticalInsights(insights);
    }

    /**
     * Statistical analysis result DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticalAnalysisResult {
        private String transactionId;
        private String userId;
        private LocalDateTime timestamp;
        
        // Overall scores
        private Double overallAnomalyScore;
        private boolean anomalous;
        
        // Outlier detection
        private Double amountZScore;
        private boolean zScoreOutlier;
        private Double iqrLowerBound;
        private Double iqrUpperBound;
        private boolean iqrOutlier;
        private Double modifiedZScore;
        private boolean modifiedZScoreOutlier;
        private Double isolationForestScore;
        private boolean isolationForestOutlier;
        private Double localOutlierFactor;
        private boolean lofOutlier;
        
        // Distribution analysis
        private Double historicalMean;
        private Double historicalMedian;
        private Double historicalStdDev;
        private Double historicalVariance;
        private Double historicalSkewness;
        private Double historicalKurtosis;
        private Double percentileRank;
        private boolean extremePercentile;
        private boolean normallyDistributed;
        private Double probabilityUnderNormal;
        private boolean unlikelyUnderNormal;
        private Double distributionEntropy;
        private boolean highEntropy;
        
        // Time series analysis
        private Double trendSlope;
        private Double trendIntercept;
        private Double trendRSquared;
        private boolean significantTrend;
        private Double predictedValue;
        private Double predictionError;
        private boolean significantDeviation;
        private Double movingAverage;
        private Double movingStdDev;
        private boolean outsideMovingRange;
        private Double autocorrelation;
        private boolean highAutocorrelation;
        
        // Correlation analysis
        private Map<String, Double> featureCorrelations;
        private boolean unusualCorrelation;
        
        // Benford's Law
        private Double benfordChiSquare;
        private boolean benfordAnomaly;
        private Double benfordExpectedProbability;
        private boolean unusualFirstDigit;
        
        // Velocity analysis
        private Long transactionsInLastHour;
        private Long transactionsInLastDay;
        private Double averageTimeBetweenTx;
        private Double stdDevTimeBetweenTx;
        private Double velocityZScore;
        private boolean highVelocity;
        private boolean velocitySpike;
        
        // Burst detection
        private Integer burstCount;
        private boolean inBurst;
        private Integer maxBurstSize;
        private Double burstIntensity;
        private boolean highBurstActivity;
        
        // Seasonality
        private Double expectedHourlyRate;
        private boolean unusualHour;
        private Double expectedDailyRate;
        private boolean unusualDay;
        private Double expectedMonthlyRate;
        private boolean unusualDayOfMonth;
        private boolean violatesSeasonality;
        
        // Insights
        private List<String> statisticalInsights;
        
        private Long processingTimeMs;
    }

    /**
     * User statistical model
     */
    @Data
    @Builder
    private static class UserStatisticalModel {
        private String userId;
        private DescriptiveStatistics amountStats;
        private DescriptiveStatistics velocityStats;
        private Map<Integer, Double> hourlyDistribution;
        private Map<Integer, Double> dailyDistribution;
        private LocalDateTime lastUpdated;
    }

    /**
     * Time series data
     */
    @Data
    @Builder
    private static class TimeSeriesData {
        private String userId;
        private List<Double> values;
        private List<LocalDateTime> timestamps;
        private SimpleRegression trendModel;
        private Double seasonalityStrength;
    }

    /**
     * Transaction burst
     */
    @Data
    @Builder
    private static class TransactionBurst {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int transactionCount;
        private double totalAmount;
        private double intensity;
    }

    // Helper methods

    private StatisticalAnalysisResult createInsufficientDataResult(StatisticalAnalysisResult result) {
        result.setOverallAnomalyScore(0.0);
        result.setAnomalous(false);
        result.setStatisticalInsights(List.of("Insufficient historical data for statistical analysis"));
        return result;
    }

    private double calculateIsolationScore(double value, double[] data) {
        // Simplified isolation forest score
        int isolationSteps = 0;
        double min = Arrays.stream(data).min().orElse(0);
        double max = Arrays.stream(data).max().orElse(0);
        double range = max - min;
        
        if (range > 0) {
            double normalizedValue = (value - min) / range;
            isolationSteps = (int) (Math.log(data.length) * (1 - normalizedValue));
        }
        
        return Math.exp(-isolationSteps / Math.log(data.length));
    }

    private double calculateLocalOutlierFactor(double value, double[] data) {
        // Simplified LOF calculation
        Arrays.sort(data);
        int k = Math.min(5, data.length / 10);
        
        double[] distances = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            distances[i] = Math.abs(data[i] - value);
        }
        Arrays.sort(distances);
        
        double kDistance = distances[Math.min(k, distances.length - 1)];
        double avgKDistance = Arrays.stream(distances).limit(k).average().orElse(0);
        
        return avgKDistance > 0 ? kDistance / avgKDistance : 1.0;
    }

    private double calculatePercentileRank(double value, double[] data) {
        long countBelow = Arrays.stream(data).filter(d -> d < value).count();
        return (double) countBelow / data.length * 100;
    }

    private boolean testNormalDistribution(double[] data) {
        // Simplified normality test using skewness and kurtosis
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double skewness = stats.getSkewness();
        double kurtosis = stats.getKurtosis();
        
        return Math.abs(skewness) < 2.0 && Math.abs(kurtosis - 3.0) < 2.0;
    }

    private double calculateEntropy(double[] data) {
        // Calculate Shannon entropy
        Map<Double, Integer> frequencies = new HashMap<>();
        for (double value : data) {
            frequencies.merge(value, 1, Integer::sum);
        }
        
        double entropy = 0.0;
        int total = data.length;
        
        for (int freq : frequencies.values()) {
            if (freq > 0) {
                double probability = (double) freq / total;
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }
        
        return entropy;
    }

    private double calculateMovingAverage(double[] data, int windowSize) {
        if (data.length < windowSize) return Arrays.stream(data).average().orElse(0);
        
        return Arrays.stream(data, data.length - windowSize, data.length)
            .average()
            .orElse(0);
    }

    private double calculateMovingStdDev(double[] data, int windowSize) {
        if (data.length < windowSize) {
            DescriptiveStatistics stats = new DescriptiveStatistics(data);
            return stats.getStandardDeviation();
        }
        
        double[] window = Arrays.copyOfRange(data, data.length - windowSize, data.length);
        DescriptiveStatistics stats = new DescriptiveStatistics(window);
        return stats.getStandardDeviation();
    }

    private double calculateAutocorrelation(double[] data, int lag) {
        if (data.length <= lag) return 0.0;
        
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double mean = stats.getMean();
        
        double numerator = 0.0;
        double denominator = 0.0;
        
        for (int i = lag; i < data.length; i++) {
            numerator += (data[i] - mean) * (data[i - lag] - mean);
        }
        
        for (double value : data) {
            denominator += Math.pow(value - mean, 2);
        }
        
        return denominator > 0 ? numerator / denominator : 0.0;
    }

    private double calculateHistoricalAverageDailyTransactions(List<TransactionPattern> patterns) {
        if (patterns.isEmpty()) return 0.0;
        
        Map<LocalDateTime, Long> dailyCounts = patterns.stream()
            .collect(Collectors.groupingBy(
                p -> p.getTimestamp().toLocalDate().atStartOfDay(),
                Collectors.counting()
            ));
        
        return dailyCounts.values().stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
    }

    private List<TransactionBurst> detectTransactionBursts(List<TransactionPattern> patterns) {
        List<TransactionBurst> bursts = new ArrayList<>();
        
        if (patterns.size() < 3) return bursts;
        
        List<TransactionPattern> sorted = patterns.stream()
            .sorted(Comparator.comparing(TransactionPattern::getTimestamp))
            .collect(Collectors.toList());
        
        int burstThreshold = 5; // Minimum transactions for a burst
        int timeWindowMinutes = 10;
        
        int i = 0;
        while (i < sorted.size()) {
            int j = i + 1;
            LocalDateTime burstStart = sorted.get(i).getTimestamp();
            double totalAmount = sorted.get(i).getAmount().doubleValue();
            
            while (j < sorted.size() && 
                   ChronoUnit.MINUTES.between(burstStart, sorted.get(j).getTimestamp()) <= timeWindowMinutes) {
                totalAmount += sorted.get(j).getAmount().doubleValue();
                j++;
            }
            
            if (j - i >= burstThreshold) {
                bursts.add(TransactionBurst.builder()
                    .startTime(burstStart)
                    .endTime(sorted.get(j - 1).getTimestamp())
                    .transactionCount(j - i)
                    .totalAmount(totalAmount)
                    .intensity((j - i) / (double) timeWindowMinutes)
                    .build());
            }
            
            i = j;
        }
        
        return bursts;
    }

    private boolean isTransactionInBurst(TransactionData transaction, TransactionBurst burst) {
        LocalDateTime txTime = transaction.getTimestamp();
        return !txTime.isBefore(burst.getStartTime()) && !txTime.isAfter(burst.getEndTime());
    }

    private double calculateBurstIntensity(List<TransactionBurst> bursts, int totalTransactions) {
        if (totalTransactions == 0) return 0.0;
        
        int burstTransactions = bursts.stream()
            .mapToInt(b -> b.transactionCount)
            .sum();
        
        return (double) burstTransactions / totalTransactions;
    }

    private Map<Integer, Double> calculateHourlyPattern(List<TransactionPattern> patterns) {
        Map<Integer, Long> hourlyCounts = patterns.stream()
            .collect(Collectors.groupingBy(
                TransactionPattern::getHourOfDay,
                Collectors.counting()
            ));
        
        long total = patterns.size();
        Map<Integer, Double> hourlyRates = new HashMap<>();
        
        for (int hour = 0; hour < 24; hour++) {
            long count = hourlyCounts.getOrDefault(hour, 0L);
            hourlyRates.put(hour, (double) count / total);
        }
        
        return hourlyRates;
    }

    private Map<Integer, Double> calculateDailyPattern(List<TransactionPattern> patterns) {
        Map<Integer, Long> dailyCounts = patterns.stream()
            .collect(Collectors.groupingBy(
                TransactionPattern::getDayOfWeek,
                Collectors.counting()
            ));
        
        long total = patterns.size();
        Map<Integer, Double> dailyRates = new HashMap<>();
        
        for (int day = 1; day <= 7; day++) {
            long count = dailyCounts.getOrDefault(day, 0L);
            dailyRates.put(day, (double) count / total);
        }
        
        return dailyRates;
    }

    private Map<Integer, Double> calculateMonthlyPattern(List<TransactionPattern> patterns) {
        Map<Integer, Long> monthlyCounts = patterns.stream()
            .collect(Collectors.groupingBy(
                p -> p.getTimestamp().getDayOfMonth(),
                Collectors.counting()
            ));
        
        long total = patterns.size();
        Map<Integer, Double> monthlyRates = new HashMap<>();
        
        for (int day = 1; day <= 31; day++) {
            long count = monthlyCounts.getOrDefault(day, 0L);
            monthlyRates.put(day, (double) count / total);
        }
        
        return monthlyRates;
    }

    /**
     * Calculate statistical risk score
     */
    public double calculateStatisticalRiskScore(StatisticalAnalysisResult result) {
        if (result == null) return 0.0;
        
        return Math.min(result.getOverallAnomalyScore() * 100, 100.0);
    }
}