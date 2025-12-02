package com.waqiti.ml.service;

import com.waqiti.ml.dto.BehaviorAnalysisResult;
import com.waqiti.ml.dto.TransactionData;
import com.waqiti.ml.dto.UserRiskProfile;
import com.waqiti.ml.entity.UserBehaviorProfile;
import com.waqiti.ml.entity.TransactionPattern;
import com.waqiti.ml.entity.BehaviorMetrics;
import com.waqiti.ml.repository.UserBehaviorProfileRepository;
import com.waqiti.ml.repository.TransactionPatternRepository;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production-ready User Behavior Profile Service with advanced ML capabilities.
 * Implements sophisticated behavioral analysis, pattern recognition, and anomaly detection.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBehaviorProfileService {

    private final UserBehaviorProfileRepository behaviorProfileRepository;
    private final TransactionPatternRepository transactionPatternRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MachineLearningModelService mlModelService;
    private final StatisticalAnalysisService statisticalAnalysisService;

    private static final String CACHE_PREFIX = "user:behavior:";
    private static final String PATTERN_CACHE_PREFIX = "pattern:";
    private static final int MAX_PATTERN_HISTORY = 1000;
    private static final double ANOMALY_THRESHOLD = 2.5; // Standard deviations

    /**
     * Comprehensive behavioral analysis with ML-powered pattern recognition
     */
    @Traced(operation = "behavior_analysis")
    public BehaviorAnalysisResult analyzeBehavior(TransactionData transaction) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Starting behavioral analysis for user: {}, transaction: {}", 
                transaction.getUserId(), transaction.getTransactionId());

            UserBehaviorProfile profile = getOrCreateUserProfile(transaction.getUserId());
            BehaviorAnalysisResult result = performComprehensiveAnalysis(transaction, profile);
            
            // Async profile update to avoid blocking
            CompletableFuture.runAsync(() -> updateUserProfileAsync(transaction, profile));
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Behavioral analysis completed in {}ms for user: {}, risk score: {}", 
                duration, transaction.getUserId(), result.getRiskScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in behavioral analysis for transaction: {}", transaction.getTransactionId(), e);
            throw new MLProcessingException("Failed to analyze user behavior", e);
        }
    }

    /**
     * Retrieve user profile with caching and fallback mechanisms
     */
    @Cacheable(value = "userBehaviorProfiles", key = "#userId")
    public UserBehaviorProfile getOrCreateUserProfile(String userId) {
        try {
            return behaviorProfileRepository.findByUserId(userId)
                .orElseGet(() -> createNewUserProfile(userId));
        } catch (Exception e) {
            log.warn("Database error retrieving user profile, using cache fallback: {}", userId, e);
            return getCachedProfileOrDefault(userId);
        }
    }

    /**
     * Perform comprehensive behavioral analysis using multiple ML models
     */
    private BehaviorAnalysisResult performComprehensiveAnalysis(TransactionData transaction, UserBehaviorProfile profile) {
        BehaviorAnalysisResult result = new BehaviorAnalysisResult();
        result.setUserId(transaction.getUserId());
        result.setTransactionId(transaction.getTransactionId());
        result.setTimestamp(LocalDateTime.now());
        result.setAnalysisVersion("2.1");
        
        // Multi-dimensional risk analysis
        Map<String, Double> riskComponents = new HashMap<>();
        
        // 1. Temporal pattern analysis
        double temporalRisk = analyzeTemporalPatterns(transaction, profile);
        riskComponents.put("temporal", temporalRisk);
        
        // 2. Amount pattern analysis
        double amountRisk = analyzeAmountPatterns(transaction, profile);
        riskComponents.put("amount", amountRisk);
        
        // 3. Frequency analysis
        double frequencyRisk = analyzeFrequencyPatterns(transaction, profile);
        riskComponents.put("frequency", frequencyRisk);
        
        // 4. Recipient analysis
        double recipientRisk = analyzeRecipientPatterns(transaction, profile);
        riskComponents.put("recipient", recipientRisk);
        
        // 5. ML model prediction
        double mlRisk = mlModelService.predictFraudRisk(transaction, profile);
        riskComponents.put("ml_prediction", mlRisk);
        
        // 6. Anomaly detection
        double anomalyRisk = detectAnomalies(transaction, profile);
        riskComponents.put("anomaly", anomalyRisk);
        
        // Weighted risk calculation
        double finalRiskScore = calculateWeightedRiskScore(riskComponents);
        
        result.setRiskScore(finalRiskScore);
        result.setRiskLevel(determineRiskLevel(finalRiskScore));
        result.setRiskComponents(riskComponents);
        result.setConfidenceScore(calculateConfidenceScore(profile, riskComponents));
        result.setRecommendedAction(determineRecommendedAction(finalRiskScore, riskComponents));
        
        // Generate detailed analysis report
        result.setAnalysisDetails(generateAnalysisDetails(riskComponents, profile));
        result.setModelVersion(mlModelService.getCurrentModelVersion());
        
        return result;
    }

    /**
     * Advanced temporal pattern analysis using time-series analysis
     */
    private double analyzeTemporalPatterns(TransactionData transaction, UserBehaviorProfile profile) {
        try {
            LocalDateTime transactionTime = transaction.getTimestamp();
            List<TransactionPattern> historicalPatterns = getHistoricalPatterns(profile.getUserId(), 30);
            
            if (historicalPatterns.isEmpty()) {
                return 15.0; // New user risk
            }
            
            // Time-of-day analysis
            int hour = transactionTime.getHour();
            double hourFrequency = calculateHourFrequency(historicalPatterns, hour);
            double hourRisk = hourFrequency < 0.05 ? 25.0 : (hourFrequency < 0.15 ? 10.0 : 0.0);
            
            // Day-of-week analysis
            int dayOfWeek = transactionTime.getDayOfWeek().getValue();
            double dayFrequency = calculateDayFrequency(historicalPatterns, dayOfWeek);
            double dayRisk = dayFrequency < 0.1 ? 15.0 : 0.0;
            
            // Consecutive transaction timing
            double velocityRisk = analyzeTransactionVelocity(transaction, profile);
            
            return Math.min(hourRisk + dayRisk + velocityRisk, 50.0);
            
        } catch (Exception e) {
            log.warn("Error in temporal analysis for user: {}", profile.getUserId(), e);
            return 20.0; // Conservative risk score on error
        }
    }

    /**
     * Advanced amount pattern analysis with statistical modeling
     */
    private double analyzeAmountPatterns(TransactionData transaction, UserBehaviorProfile profile) {
        try {
            BigDecimal transactionAmount = transaction.getAmount();
            List<BigDecimal> historicalAmounts = getHistoricalAmounts(profile.getUserId(), 90);
            
            if (historicalAmounts.size() < 5) {
                return transactionAmount.compareTo(BigDecimal.valueOf(1000)) > 0 ? 30.0 : 10.0;
            }
            
            // Statistical analysis
            Map<String, Double> statistics = statisticalAnalysisService.calculateStatistics(historicalAmounts);
            double mean = statistics.get("mean");
            double stdDev = statistics.get("standardDeviation");
            double median = statistics.get("median");
            
            // Z-score calculation
            double zScore = Math.abs((transactionAmount.doubleValue() - mean) / stdDev);
            
            // Percentile analysis
            double percentile = statisticalAnalysisService.calculatePercentile(historicalAmounts, transactionAmount);
            
            // Risk scoring based on statistical deviation
            double zScoreRisk = zScore > 3.0 ? 40.0 : (zScore > 2.0 ? 25.0 : (zScore > 1.5 ? 15.0 : 0.0));
            double percentileRisk = percentile > 95.0 ? 20.0 : (percentile > 90.0 ? 10.0 : 0.0);
            
            // Round number detection (potential money laundering indicator)
            double roundNumberRisk = isRoundNumber(transactionAmount) ? 10.0 : 0.0;
            
            return Math.min(zScoreRisk + percentileRisk + roundNumberRisk, 50.0);
            
        } catch (Exception e) {
            log.warn("Error in amount analysis for user: {}", profile.getUserId(), e);
            return 15.0;
        }
    }

    /**
     * Advanced frequency analysis with velocity tracking
     */
    private double analyzeFrequencyPatterns(TransactionData transaction, UserBehaviorProfile profile) {
        try {
            String userId = transaction.getUserId();
            LocalDateTime now = LocalDateTime.now();
            
            // Multi-timeframe frequency analysis
            long transactions1Hour = countTransactions(userId, now.minus(1, ChronoUnit.HOURS), now);
            long transactions1Day = countTransactions(userId, now.minus(1, ChronoUnit.DAYS), now);
            long transactions7Days = countTransactions(userId, now.minus(7, ChronoUnit.DAYS), now);
            
            // Calculate velocity metrics
            BehaviorMetrics metrics = profile.getBehaviorMetrics();
            double avgHourlyRate = metrics != null ? metrics.getAverageHourlyTransactionRate() : 0.5;
            double avgDailyRate = metrics != null ? metrics.getAverageDailyTransactionRate() : 5.0;
            
            // Risk calculation based on deviation from normal patterns
            double hourlyRisk = transactions1Hour > (avgHourlyRate * 3) ? 30.0 : 
                               (transactions1Hour > (avgHourlyRate * 2) ? 15.0 : 0.0);
            
            double dailyRisk = transactions1Day > (avgDailyRate * 2.5) ? 25.0 : 
                              (transactions1Day > (avgDailyRate * 1.5) ? 10.0 : 0.0);
            
            // Burst detection
            double burstRisk = detectTransactionBursts(userId, now);
            
            return Math.min(hourlyRisk + dailyRisk + burstRisk, 50.0);
            
        } catch (Exception e) {
            log.warn("Error in frequency analysis for user: {}", profile.getUserId(), e);
            return 10.0;
        }
    }

    /**
     * Recipient pattern analysis for relationship mapping
     */
    private double analyzeRecipientPatterns(TransactionData transaction, UserBehaviorProfile profile) {
        try {
            String targetAccount = transaction.getTargetAccount();
            if (targetAccount == null) return 0.0;
            
            // Check if recipient is in user's frequent contacts
            List<String> frequentRecipients = getFrequentRecipients(profile.getUserId(), 90);
            boolean isFrequentRecipient = frequentRecipients.contains(targetAccount);
            
            if (isFrequentRecipient) {
                return 0.0; // No risk for frequent recipients
            }
            
            // New recipient analysis
            boolean isNewRecipient = !hasTransactedBefore(profile.getUserId(), targetAccount);
            if (isNewRecipient && transaction.getAmount().compareTo(BigDecimal.valueOf(500)) > 0) {
                return 20.0; // Higher risk for large amounts to new recipients
            }
            
            // Cross-reference with known fraudulent accounts
            if (isSuspiciousRecipient(targetAccount)) {
                return 40.0;
            }
            
            return isNewRecipient ? 5.0 : 0.0;
            
        } catch (Exception e) {
            log.warn("Error in recipient analysis for user: {}", profile.getUserId(), e);
            return 5.0;
        }
    }

    /**
     * Advanced anomaly detection using isolation forests and clustering
     */
    private double detectAnomalies(TransactionData transaction, UserBehaviorProfile profile) {
        try {
            // Multi-dimensional feature vector
            double[] featureVector = createFeatureVector(transaction, profile);
            
            // ML-based anomaly detection
            double anomalyScore = mlModelService.detectAnomalies(featureVector);
            
            // Statistical outlier detection
            double statisticalAnomalyScore = statisticalAnalysisService.detectOutliers(
                transaction, getHistoricalTransactions(profile.getUserId(), 30));
            
            // Combine scores with weights
            double combinedAnomalyScore = (anomalyScore * 0.7) + (statisticalAnomalyScore * 0.3);
            
            return Math.min(combinedAnomalyScore * 50.0, 50.0);
            
        } catch (Exception e) {
            log.warn("Error in anomaly detection for user: {}", profile.getUserId(), e);
            return 10.0;
        }
    }

    /**
     * Weighted risk score calculation with dynamic weights
     */
    private double calculateWeightedRiskScore(Map<String, Double> riskComponents) {
        Map<String, Double> weights = Map.of(
            "temporal", 0.15,
            "amount", 0.25,
            "frequency", 0.20,
            "recipient", 0.15,
            "ml_prediction", 0.35,
            "anomaly", 0.20
        );
        
        double weightedScore = riskComponents.entrySet().stream()
            .mapToDouble(entry -> entry.getValue() * weights.getOrDefault(entry.getKey(), 0.1))
            .sum();
        
        return Math.min(weightedScore, 100.0);
    }

    /**
     * Determine recommended action based on risk profile
     */
    private String determineRecommendedAction(double riskScore, Map<String, Double> riskComponents) {
        if (riskScore >= 80.0) return "BLOCK_TRANSACTION";
        if (riskScore >= 60.0) return "MANUAL_REVIEW";
        if (riskScore >= 40.0) return "ADDITIONAL_VERIFICATION";
        if (riskScore >= 20.0) return "ENHANCED_MONITORING";
        return "APPROVE";
    }

    /**
     * Calculate confidence score based on data quality and model certainty
     */
    private double calculateConfidenceScore(UserBehaviorProfile profile, Map<String, Double> riskComponents) {
        // Base confidence on historical data availability
        long historicalDataPoints = profile.getTotalTransactionCount();
        double dataConfidence = Math.min(historicalDataPoints / 100.0, 1.0);
        
        // Model confidence from ML service
        double modelConfidence = mlModelService.getModelConfidence();
        
        // Variance in risk components (lower variance = higher confidence)
        double variance = statisticalAnalysisService.calculateVariance(
            riskComponents.values().stream().mapToDouble(Double::doubleValue).toArray());
        double varianceConfidence = Math.max(0.0, 1.0 - (variance / 100.0));
        
        return (dataConfidence * 0.4) + (modelConfidence * 0.4) + (varianceConfidence * 0.2);
    }

    /**
     * Async profile update with optimistic locking
     */
    @Transactional
    @CacheEvict(value = "userBehaviorProfiles", key = "#profile.userId")
    public void updateUserProfileAsync(TransactionData transaction, UserBehaviorProfile profile) {
        try {
            // Update transaction patterns
            TransactionPattern pattern = createTransactionPattern(transaction);
            transactionPatternRepository.save(pattern);
            
            // Update behavior metrics
            updateBehaviorMetrics(profile, transaction);
            
            // Update ML features
            updateMLFeatures(profile, transaction);
            
            // Save profile with optimistic locking
            profile.setLastUpdated(LocalDateTime.now());
            profile.incrementVersion();
            behaviorProfileRepository.save(profile);
            
            // Update Redis cache for fast access
            updateRedisCache(profile);
            
        } catch (Exception e) {
            log.error("Error updating user profile asynchronously: {}", profile.getUserId(), e);
        }
    }

    /**
     * Create comprehensive transaction pattern
     */
    private TransactionPattern createTransactionPattern(TransactionData transaction) {
        TransactionPattern pattern = new TransactionPattern();
        pattern.setUserId(transaction.getUserId());
        pattern.setTransactionId(transaction.getTransactionId());
        pattern.setAmount(transaction.getAmount());
        pattern.setCurrency(transaction.getCurrency());
        pattern.setTimestamp(transaction.getTimestamp());
        pattern.setHourOfDay(transaction.getTimestamp().getHour());
        pattern.setDayOfWeek(transaction.getTimestamp().getDayOfWeek().getValue());
        pattern.setTransactionType(transaction.getTransactionType());
        pattern.setTargetAccount(transaction.getTargetAccount());
        pattern.setDeviceId(transaction.getDeviceId());
        pattern.setIpAddress(transaction.getIpAddress());
        pattern.setLocation(transaction.getLocation());
        return pattern;
    }

    /**
     * Update behavioral metrics with running statistics
     */
    private void updateBehaviorMetrics(UserBehaviorProfile profile, TransactionData transaction) {
        BehaviorMetrics metrics = profile.getBehaviorMetrics();
        if (metrics == null) {
            metrics = new BehaviorMetrics();
            profile.setBehaviorMetrics(metrics);
        }
        
        // Update transaction counts
        metrics.incrementTotalTransactions();
        
        // Update amount statistics
        BigDecimal currentAmount = transaction.getAmount();
        if (metrics.getTotalAmount() == null) {
            metrics.setTotalAmount(currentAmount);
            metrics.setMinAmount(currentAmount);
            metrics.setMaxAmount(currentAmount);
        } else {
            metrics.setTotalAmount(metrics.getTotalAmount().add(currentAmount));
            if (currentAmount.compareTo(metrics.getMinAmount()) < 0) {
                metrics.setMinAmount(currentAmount);
            }
            if (currentAmount.compareTo(metrics.getMaxAmount()) > 0) {
                metrics.setMaxAmount(currentAmount);
            }
        }
        
        // Calculate running average
        long totalCount = metrics.getTotalTransactions();
        BigDecimal averageAmount = metrics.getTotalAmount().divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
        metrics.setAverageAmount(averageAmount);
        
        // Update hourly patterns
        updateHourlyPatterns(metrics, transaction.getTimestamp());
        
        // Update velocity metrics
        updateVelocityMetrics(metrics, transaction);
    }

    /**
     * Advanced ML feature engineering
     */
    private void updateMLFeatures(UserBehaviorProfile profile, TransactionData transaction) {
        Map<String, Object> features = profile.getMlFeatures();
        if (features == null) {
            features = new HashMap<>();
            profile.setMlFeatures(features);
        }
        
        // Time-based features
        features.put("avg_transaction_hour", calculateAverageTransactionHour(profile));
        features.put("transaction_time_entropy", calculateTimeEntropy(profile));
        
        // Amount-based features
        features.put("amount_variance", calculateAmountVariance(profile));
        features.put("amount_skewness", calculateAmountSkewness(profile));
        
        // Network features
        features.put("unique_recipients_ratio", calculateUniqueRecipientsRatio(profile));
        features.put("cross_border_ratio", calculateCrossBorderRatio(profile));
        
        // Behavioral features
        features.put("weekend_transaction_ratio", calculateWeekendRatio(profile));
        features.put("night_transaction_ratio", calculateNightTransactionRatio(profile));
    }

    /**
     * Helper methods for pattern analysis
     */
    private List<TransactionPattern> getHistoricalPatterns(String userId, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
        return transactionPatternRepository.findByUserIdAndTimestampAfter(userId, cutoff);
    }
    
    private List<BigDecimal> getHistoricalAmounts(String userId, int days) {
        return getHistoricalPatterns(userId, days).stream()
            .map(TransactionPattern::getAmount)
            .collect(Collectors.toList());
    }
    
    private List<TransactionData> getHistoricalTransactions(String userId, int days) {
        return getHistoricalPatterns(userId, days).stream()
            .map(this::convertPatternToTransactionData)
            .collect(Collectors.toList());
    }

    private double calculateHourFrequency(List<TransactionPattern> patterns, int hour) {
        long hourCount = patterns.stream()
            .mapToInt(p -> p.getTimestamp().getHour())
            .filter(h -> h == hour)
            .count();
        return patterns.isEmpty() ? 0.0 : (double) hourCount / patterns.size();
    }

    private double calculateDayFrequency(List<TransactionPattern> patterns, int dayOfWeek) {
        long dayCount = patterns.stream()
            .mapToInt(p -> p.getTimestamp().getDayOfWeek().getValue())
            .filter(d -> d == dayOfWeek)
            .count();
        return patterns.isEmpty() ? 0.0 : (double) dayCount / patterns.size();
    }

    private double analyzeTransactionVelocity(TransactionData transaction, UserBehaviorProfile profile) {
        String cacheKey = CACHE_PREFIX + "velocity:" + profile.getUserId();
        LocalDateTime lastTransaction = (LocalDateTime) redisTemplate.opsForValue().get(cacheKey);
        
        if (lastTransaction != null) {
            long minutesBetween = ChronoUnit.MINUTES.between(lastTransaction, transaction.getTimestamp());
            if (minutesBetween < 1) return 40.0; // Very fast consecutive transactions
            if (minutesBetween < 5) return 20.0; // Fast consecutive transactions
        }
        
        // Update cache
        redisTemplate.opsForValue().set(cacheKey, transaction.getTimestamp(), 1, TimeUnit.HOURS);
        
        return 0.0;
    }

    private long countTransactions(String userId, LocalDateTime start, LocalDateTime end) {
        return transactionPatternRepository.countByUserIdAndTimestampBetween(userId, start, end);
    }

    private List<String> getFrequentRecipients(String userId, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
        return transactionPatternRepository.findFrequentRecipients(userId, cutoff, 5);
    }

    private boolean hasTransactedBefore(String userId, String targetAccount) {
        return transactionPatternRepository.existsByUserIdAndTargetAccount(userId, targetAccount);
    }

    private boolean isSuspiciousRecipient(String targetAccount) {
        String cacheKey = "suspicious:account:" + targetAccount;
        Boolean suspicious = (Boolean) redisTemplate.opsForValue().get(cacheKey);
        return Boolean.TRUE.equals(suspicious);
    }

    private boolean isRoundNumber(BigDecimal amount) {
        return amount.remainder(BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO) == 0 ||
               amount.remainder(BigDecimal.valueOf(50)).compareTo(BigDecimal.ZERO) == 0;
    }

    private double detectTransactionBursts(String userId, LocalDateTime now) {
        List<LocalDateTime> recent = transactionPatternRepository
            .findRecentTransactionTimes(userId, now.minus(10, ChronoUnit.MINUTES));
        
        if (recent.size() >= 5) {
            return 35.0; // Transaction burst detected
        }
        
        return 0.0;
    }

    private double[] createFeatureVector(TransactionData transaction, UserBehaviorProfile profile) {
        return new double[] {
            transaction.getAmount().doubleValue(),
            transaction.getTimestamp().getHour(),
            transaction.getTimestamp().getDayOfWeek().getValue(),
            profile.getTotalTransactionCount(),
            profile.getBehaviorMetrics() != null ? 
                profile.getBehaviorMetrics().getAverageAmount().doubleValue() : 0.0,
            // Add more features as needed
        };
    }

    /**
     * Create new user profile with initial configuration
     */
    @Transactional
    private UserBehaviorProfile createNewUserProfile(String userId) {
        UserBehaviorProfile profile = new UserBehaviorProfile();
        profile.setUserId(userId);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setLastUpdated(LocalDateTime.now());
        profile.setTotalTransactionCount(0L);
        profile.setRiskScore(50.0); // Initial moderate risk for new users
        profile.setBehaviorMetrics(new BehaviorMetrics());
        profile.setMlFeatures(new HashMap<>());
        profile.setVersion(1L);
        
        return behaviorProfileRepository.save(profile);
    }

    /**
     * Cache fallback for profile retrieval
     */
    private UserBehaviorProfile getCachedProfileOrDefault(String userId) {
        String cacheKey = CACHE_PREFIX + userId;
        UserBehaviorProfile cached = (UserBehaviorProfile) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;
        }
        
        // Return default profile if cache miss
        return createDefaultProfile(userId);
    }

    private UserBehaviorProfile createDefaultProfile(String userId) {
        UserBehaviorProfile profile = new UserBehaviorProfile();
        profile.setUserId(userId);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setLastUpdated(LocalDateTime.now());
        profile.setTotalTransactionCount(0L);
        profile.setRiskScore(50.0);
        profile.setBehaviorMetrics(new BehaviorMetrics());
        profile.setMlFeatures(new HashMap<>());
        return profile;
    }

    private void updateRedisCache(UserBehaviorProfile profile) {
        String cacheKey = CACHE_PREFIX + profile.getUserId();
        redisTemplate.opsForValue().set(cacheKey, profile, 30, TimeUnit.MINUTES);
    }

    private String determineRiskLevel(double riskScore) {
        if (riskScore >= 80) return "CRITICAL";
        if (riskScore >= 60) return "HIGH";
        if (riskScore >= 40) return "MEDIUM";
        if (riskScore >= 20) return "LOW";
        return "MINIMAL";
    }

    private String generateAnalysisDetails(Map<String, Double> riskComponents, UserBehaviorProfile profile) {
        StringBuilder details = new StringBuilder();
        details.append("Behavioral Analysis Report:\n");

        riskComponents.forEach((component, score) ->
            details.append(String.format("- %s: %.2f\n", component, score)));

        details.append(String.format("Historical transactions: %d\n", profile.getTotalTransactionCount()));
        details.append(String.format("Profile age: %d days\n",
            ChronoUnit.DAYS.between(profile.getCreatedAt(), LocalDateTime.now())));
        
        return details.toString();
    }

    // Additional helper methods for advanced features
    private void updateHourlyPatterns(BehaviorMetrics metrics, LocalDateTime timestamp) {
        Map<Integer, Long> hourlyPatterns = metrics.getHourlyPatterns();
        if (hourlyPatterns == null) {
            hourlyPatterns = new HashMap<>();
            metrics.setHourlyPatterns(hourlyPatterns);
        }
        
        int hour = timestamp.getHour();
        hourlyPatterns.merge(hour, 1L, Long::sum);
    }

    private void updateVelocityMetrics(BehaviorMetrics metrics, TransactionData transaction) {
        LocalDateTime now = LocalDateTime.now();
        
        // Update hourly rate
        long recentHourlyTransactions = countRecentTransactions(transaction.getUserId(), 1, ChronoUnit.HOURS);
        metrics.setCurrentHourlyRate(recentHourlyTransactions);
        
        // Update daily rate
        long recentDailyTransactions = countRecentTransactions(transaction.getUserId(), 1, ChronoUnit.DAYS);
        metrics.setCurrentDailyRate(recentDailyTransactions);
        
        // Calculate running averages
        if (metrics.getAverageHourlyTransactionRate() == 0.0) {
            metrics.setAverageHourlyTransactionRate(recentHourlyTransactions);
        } else {
            double newAvg = (metrics.getAverageHourlyTransactionRate() * 0.9) + (recentHourlyTransactions * 0.1);
            metrics.setAverageHourlyTransactionRate(newAvg);
        }
    }

    private long countRecentTransactions(String userId, long amount, ChronoUnit unit) {
        LocalDateTime cutoff = LocalDateTime.now().minus(amount, unit);
        return transactionPatternRepository.countByUserIdAndTimestampAfter(userId, cutoff);
    }

    private double calculateAverageTransactionHour(UserBehaviorProfile profile) {
        List<TransactionPattern> patterns = getHistoricalPatterns(profile.getUserId(), 30);
        return patterns.stream()
            .mapToInt(p -> p.getTimestamp().getHour())
            .average()
            .orElse(12.0);
    }

    private double calculateTimeEntropy(UserBehaviorProfile profile) {
        List<TransactionPattern> patterns = getHistoricalPatterns(profile.getUserId(), 30);
        Map<Integer, Long> hourCounts = patterns.stream()
            .collect(Collectors.groupingBy(
                p -> p.getTimestamp().getHour(),
                Collectors.counting()));
        
        return statisticalAnalysisService.calculateEntropy(hourCounts);
    }

    private double calculateAmountVariance(UserBehaviorProfile profile) {
        List<BigDecimal> amounts = getHistoricalAmounts(profile.getUserId(), 30);
        return statisticalAnalysisService.calculateVariance(
            amounts.stream().mapToDouble(BigDecimal::doubleValue).toArray());
    }

    private double calculateAmountSkewness(UserBehaviorProfile profile) {
        List<BigDecimal> amounts = getHistoricalAmounts(profile.getUserId(), 30);
        return statisticalAnalysisService.calculateSkewness(
            amounts.stream().mapToDouble(BigDecimal::doubleValue).toArray());
    }

    private double calculateUniqueRecipientsRatio(UserBehaviorProfile profile) {
        List<TransactionPattern> patterns = getHistoricalPatterns(profile.getUserId(), 30);
        long uniqueRecipients = patterns.stream()
            .map(TransactionPattern::getTargetAccount)
            .distinct()
            .count();
        
        return patterns.isEmpty() ? 0.0 : (double) uniqueRecipients / patterns.size();
    }

    private double calculateCrossBorderRatio(UserBehaviorProfile profile) {
        List<TransactionPattern> patterns = getHistoricalPatterns(profile.getUserId(), 30);
        long crossBorderCount = patterns.stream()
            .filter(p -> isCrossBorderTransaction(p))
            .count();
        
        return patterns.isEmpty() ? 0.0 : (double) crossBorderCount / patterns.size();
    }

    private double calculateWeekendRatio(UserBehaviorProfile profile) {
        List<TransactionPattern> patterns = getHistoricalPatterns(profile.getUserId(), 30);
        long weekendCount = patterns.stream()
            .filter(p -> isWeekend(p.getTimestamp()))
            .count();
        
        return patterns.isEmpty() ? 0.0 : (double) weekendCount / patterns.size();
    }

    private double calculateNightTransactionRatio(UserBehaviorProfile profile) {
        List<TransactionPattern> patterns = getHistoricalPatterns(profile.getUserId(), 30);
        long nightCount = patterns.stream()
            .filter(p -> isNightTime(p.getTimestamp()))
            .count();
        
        return patterns.isEmpty() ? 0.0 : (double) nightCount / patterns.size();
    }

    private boolean isCrossBorderTransaction(TransactionPattern pattern) {
        // Implementation would check if transaction crosses country borders
        return pattern.getLocation() != null && pattern.getLocation().contains("INTERNATIONAL");
    }

    private boolean isWeekend(LocalDateTime timestamp) {
        int dayOfWeek = timestamp.getDayOfWeek().getValue();
        return dayOfWeek == 6 || dayOfWeek == 7; // Saturday or Sunday
    }

    private boolean isNightTime(LocalDateTime timestamp) {
        int hour = timestamp.getHour();
        return hour >= 22 || hour <= 6;
    }

    private TransactionData convertPatternToTransactionData(TransactionPattern pattern) {
        TransactionData data = new TransactionData();
        data.setTransactionId(pattern.getTransactionId());
        data.setUserId(pattern.getUserId());
        data.setAmount(pattern.getAmount());
        data.setCurrency(pattern.getCurrency());
        data.setTimestamp(pattern.getTimestamp());
        data.setTargetAccount(pattern.getTargetAccount());
        data.setTransactionType(pattern.getTransactionType());
        data.setDeviceId(pattern.getDeviceId());
        data.setIpAddress(pattern.getIpAddress());
        data.setLocation(pattern.getLocation());
        return data;
    }
}