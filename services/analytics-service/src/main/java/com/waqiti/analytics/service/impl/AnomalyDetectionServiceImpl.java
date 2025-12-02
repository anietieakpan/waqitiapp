package com.waqiti.analytics.service.impl;

import com.waqiti.analytics.domain.TransactionAnalytics;
import com.waqiti.analytics.dto.anomaly.TransactionAnomaly;
import com.waqiti.analytics.repository.TransactionAnalyticsRepository;
import com.waqiti.analytics.service.AnomalyDetectionService;
import com.waqiti.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private final TransactionAnalyticsRepository analyticsRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Machine learning thresholds
    private static final double DEFAULT_ANOMALY_THRESHOLD = 2.5; // Standard deviations
    private static final double AMOUNT_THRESHOLD_MULTIPLIER = 3.0;
    private static final double FREQUENCY_THRESHOLD_MULTIPLIER = 2.0;
    private static final double LOCATION_ANOMALY_THRESHOLD = 0.8;
    private static final int HISTORICAL_DAYS_ANALYSIS = 90;

    @Override
    public List<TransactionAnomaly> detectAnomalies(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Detecting anomalies for user: {} between {} and {}", userId, startDate, endDate);
        
        try {
            // Get user's historical transaction patterns
            List<TransactionAnalytics> historicalTransactions = getHistoricalTransactions(userId);
            List<TransactionAnalytics> currentTransactions = analyticsRepository
                    .findByUserIdAndTimestampBetween(userId, startDate, endDate);
            
            if (historicalTransactions.isEmpty()) {
                log.warn("No historical data for user: {}, skipping anomaly detection", userId);
                return Collections.emptyList();
            }
            
            List<TransactionAnomaly> anomalies = new ArrayList<>();
            
            // Analyze different types of anomalies
            anomalies.addAll(detectAmountAnomalies(userId, currentTransactions, historicalTransactions));
            anomalies.addAll(detectFrequencyAnomalies(userId, currentTransactions, historicalTransactions));
            anomalies.addAll(detectLocationAnomalies(userId, currentTransactions, historicalTransactions));
            anomalies.addAll(detectTimePatternAnomalies(userId, currentTransactions, historicalTransactions));
            anomalies.addAll(detectMerchantAnomalies(userId, currentTransactions, historicalTransactions));
            
            // Sort by severity and timestamp
            anomalies.sort((a, b) -> {
                int severityCompare = getSeverityWeight(b.getSeverity()) - getSeverityWeight(a.getSeverity());
                if (severityCompare != 0) return severityCompare;
                return b.getDetectedAt().compareTo(a.getDetectedAt());
            });
            
            log.info("Detected {} anomalies for user: {}", anomalies.size(), userId);
            return anomalies;
            
        } catch (Exception e) {
            log.error("Error detecting anomalies for user: {}", userId, e);
            throw new BusinessException("Failed to detect anomalies: " + e.getMessage());
        }
    }
    
    @Override
    public void checkForAnomalies(UUID userId, TransactionAnalytics transaction) {
        log.debug("Real-time anomaly check for transaction: {} user: {}", 
                transaction.getTransactionId(), userId);
        
        try {
            List<TransactionAnalytics> historicalTransactions = getHistoricalTransactions(userId);
            if (historicalTransactions.isEmpty()) {
                log.debug("No historical data for real-time anomaly check, user: {}", userId);
                return;
            }
            
            List<TransactionAnomaly> anomalies = new ArrayList<>();
            
            // Real-time checks (lighter weight)
            TransactionAnomaly amountAnomaly = checkAmountAnomaly(transaction, historicalTransactions);
            if (amountAnomaly != null) anomalies.add(amountAnomaly);
            
            TransactionAnomaly locationAnomaly = checkLocationAnomaly(transaction, historicalTransactions);
            if (locationAnomaly != null) anomalies.add(locationAnomaly);
            
            TransactionAnomaly timeAnomaly = checkTimeAnomaly(transaction, historicalTransactions);
            if (timeAnomaly != null) anomalies.add(timeAnomaly);
            
            // Publish anomalies for immediate action
            for (TransactionAnomaly anomaly : anomalies) {
                publishAnomalyAlert(anomaly);
            }
            
        } catch (Exception e) {
            log.error("Error in real-time anomaly check for user: {}", userId, e);
        }
    }
    
    @Override
    @Cacheable(value = "anomalyThresholds", key = "#userId")
    public Double getAnomalyThreshold(UUID userId) {
        try {
            // Calculate personalized threshold based on user's transaction patterns
            List<TransactionAnalytics> transactions = getHistoricalTransactions(userId);
            
            if (transactions.isEmpty()) {
                return DEFAULT_ANOMALY_THRESHOLD;
            }
            
            // Calculate variability in user's spending patterns
            double amountVariability = calculateAmountVariability(transactions);
            double frequencyVariability = calculateFrequencyVariability(transactions);
            
            // Adjust threshold based on user's natural variability
            double adjustedThreshold = DEFAULT_ANOMALY_THRESHOLD;
            
            if (amountVariability > 1.5) {
                adjustedThreshold += 0.5; // More tolerant for variable spenders
            }
            if (frequencyVariability > 1.5) {
                adjustedThreshold += 0.3; // More tolerant for irregular spenders
            }
            
            return Math.min(adjustedThreshold, 4.0); // Cap at 4 standard deviations
            
        } catch (Exception e) {
            log.error("Error calculating anomaly threshold for user: {}", userId, e);
            return DEFAULT_ANOMALY_THRESHOLD;
        }
    }
    
    @Override
    public void updateAnomalyModel(UUID userId, List<TransactionAnalytics> transactions) {
        log.info("Updating anomaly model for user: {} with {} transactions", 
                userId, transactions.size());
        
        try {
            // Clear cached threshold to force recalculation
            // cacheManager.evict("anomalyThresholds", userId);
            
            // Update user's spending patterns
            updateSpendingPatterns(userId, transactions);
            
            // Recalculate baseline metrics
            recalculateBaselineMetrics(userId, transactions);
            
            log.info("Successfully updated anomaly model for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Error updating anomaly model for user: {}", userId, e);
        }
    }
    
    // Private helper methods
    
    private List<TransactionAnalytics> getHistoricalTransactions(UUID userId) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(HISTORICAL_DAYS_ANALYSIS);
        return analyticsRepository.findByUserIdAndTimestampAfter(userId, cutoffDate);
    }
    
    private List<TransactionAnomaly> detectAmountAnomalies(UUID userId, 
            List<TransactionAnalytics> current, List<TransactionAnalytics> historical) {
        
        List<TransactionAnomaly> anomalies = new ArrayList<>();
        
        // Calculate historical amount statistics
        List<BigDecimal> amounts = historical.stream()
                .map(TransactionAnalytics::getAmount)
                .collect(Collectors.toList());
        
        if (amounts.isEmpty()) return anomalies;
        
        double mean = calculateMean(amounts);
        double stdDev = calculateStandardDeviation(amounts, mean);
        double threshold = getAnomalyThreshold(userId);
        
        for (TransactionAnalytics transaction : current) {
            double zScore = Math.abs((transaction.getAmount().doubleValue() - mean) / stdDev);
            
            if (zScore > threshold * AMOUNT_THRESHOLD_MULTIPLIER) {
                TransactionAnomaly anomaly = TransactionAnomaly.builder()
                        .anomalyId(UUID.randomUUID().toString())
                        .userId(userId)
                        .transactionId(transaction.getTransactionId())
                        .type("AMOUNT_ANOMALY")
                        .severity(calculateSeverity(zScore, threshold))
                        .description(String.format("Transaction amount $%.2f is %.1f standard deviations above normal", 
                                transaction.getAmount(), zScore))
                        .anomalyScore(BigDecimal.valueOf(zScore).setScale(2, RoundingMode.HALF_UP))
                        .detectedAt(LocalDateTime.now())
                        .metadata(Map.of(
                                "mean", mean,
                                "stdDev", stdDev,
                                "zScore", zScore,
                                "amount", transaction.getAmount()
                        ))
                        .build();
                
                anomalies.add(anomaly);
            }
        }
        
        return anomalies;
    }
    
    private List<TransactionAnomaly> detectFrequencyAnomalies(UUID userId,
            List<TransactionAnalytics> current, List<TransactionAnalytics> historical) {
        
        List<TransactionAnomaly> anomalies = new ArrayList<>();
        
        // Calculate daily transaction frequency patterns
        Map<String, Integer> dailyFrequency = new HashMap<>();
        
        for (TransactionAnalytics transaction : historical) {
            String dayKey = transaction.getTimestamp().toLocalDate().toString();
            dailyFrequency.merge(dayKey, 1, Integer::sum);
        }
        
        if (dailyFrequency.isEmpty()) return anomalies;
        
        double meanFrequency = dailyFrequency.values().stream()
                .mapToInt(Integer::intValue)
                .average().orElse(0.0);
        
        double stdDevFrequency = calculateStandardDeviation(
                dailyFrequency.values().stream()
                    .map(BigDecimal::valueOf)
                    .collect(Collectors.toList()), 
                meanFrequency);
        
        // Group current transactions by day
        Map<String, Long> currentDailyFrequency = current.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTimestamp().toLocalDate().toString(),
                        Collectors.counting()));
        
        double threshold = getAnomalyThreshold(userId);
        
        for (Map.Entry<String, Long> entry : currentDailyFrequency.entrySet()) {
            double zScore = Math.abs((entry.getValue() - meanFrequency) / stdDevFrequency);
            
            if (zScore > threshold * FREQUENCY_THRESHOLD_MULTIPLIER) {
                TransactionAnomaly anomaly = TransactionAnomaly.builder()
                        .anomalyId(UUID.randomUUID().toString())
                        .userId(userId)
                        .type("FREQUENCY_ANOMALY")
                        .severity(calculateSeverity(zScore, threshold))
                        .description(String.format("Daily transaction frequency of %d is abnormal (normal: %.1f)", 
                                entry.getValue(), meanFrequency))
                        .anomalyScore(BigDecimal.valueOf(zScore).setScale(2, RoundingMode.HALF_UP))
                        .detectedAt(LocalDateTime.now())
                        .metadata(Map.of(
                                "date", entry.getKey(),
                                "frequency", entry.getValue(),
                                "meanFrequency", meanFrequency,
                                "zScore", zScore
                        ))
                        .build();
                
                anomalies.add(anomaly);
            }
        }
        
        return anomalies;
    }
    
    private List<TransactionAnomaly> detectLocationAnomalies(UUID userId,
            List<TransactionAnalytics> current, List<TransactionAnalytics> historical) {
        
        List<TransactionAnomaly> anomalies = new ArrayList<>();
        
        // Build location frequency map
        Map<String, Integer> locationFrequency = new HashMap<>();
        for (TransactionAnalytics transaction : historical) {
            if (transaction.getLocation() != null) {
                locationFrequency.merge(transaction.getLocation(), 1, Integer::sum);
            }
        }
        
        if (locationFrequency.isEmpty()) return anomalies;
        
        int totalHistoricalTransactions = historical.size();
        
        for (TransactionAnalytics transaction : current) {
            if (transaction.getLocation() != null) {
                int locationCount = locationFrequency.getOrDefault(transaction.getLocation(), 0);
                double locationFreq = (double) locationCount / totalHistoricalTransactions;
                
                if (locationFreq < LOCATION_ANOMALY_THRESHOLD && locationCount == 0) {
                    TransactionAnomaly anomaly = TransactionAnomaly.builder()
                            .anomalyId(UUID.randomUUID().toString())
                            .userId(userId)
                            .transactionId(transaction.getTransactionId())
                            .type("LOCATION_ANOMALY")
                            .severity("MEDIUM")
                            .description(String.format("Transaction at new location: %s", 
                                    transaction.getLocation()))
                            .anomalyScore(BigDecimal.valueOf(1.0 - locationFreq))
                            .detectedAt(LocalDateTime.now())
                            .metadata(Map.of(
                                    "location", transaction.getLocation(),
                                    "isNewLocation", true
                            ))
                            .build();
                    
                    anomalies.add(anomaly);
                }
            }
        }
        
        return anomalies;
    }
    
    private List<TransactionAnomaly> detectTimePatternAnomalies(UUID userId,
            List<TransactionAnalytics> current, List<TransactionAnalytics> historical) {
        
        List<TransactionAnomaly> anomalies = new ArrayList<>();
        
        // Analyze hour-of-day patterns
        Map<Integer, Integer> hourFrequency = new HashMap<>();
        for (TransactionAnalytics transaction : historical) {
            int hour = transaction.getTimestamp().getHour();
            hourFrequency.merge(hour, 1, Integer::sum);
        }
        
        if (hourFrequency.isEmpty()) return anomalies;
        
        for (TransactionAnalytics transaction : current) {
            int hour = transaction.getTimestamp().getHour();
            int hourCount = hourFrequency.getOrDefault(hour, 0);
            
            // Check if transaction time is unusual
            if (hourCount == 0 && (hour < 6 || hour > 23)) {
                TransactionAnomaly anomaly = TransactionAnomaly.builder()
                        .anomalyId(UUID.randomUUID().toString())
                        .userId(userId)
                        .transactionId(transaction.getTransactionId())
                        .type("TIME_PATTERN_ANOMALY")
                        .severity("LOW")
                        .description(String.format("Transaction at unusual hour: %02d:00", hour))
                        .anomalyScore(BigDecimal.valueOf(0.5))
                        .detectedAt(LocalDateTime.now())
                        .metadata(Map.of(
                                "hour", hour,
                                "isUnusualTime", true
                        ))
                        .build();
                
                anomalies.add(anomaly);
            }
        }
        
        return anomalies;
    }
    
    private List<TransactionAnomaly> detectMerchantAnomalies(UUID userId,
            List<TransactionAnalytics> current, List<TransactionAnalytics> historical) {
        
        List<TransactionAnomaly> anomalies = new ArrayList<>();
        
        // Build merchant frequency map
        Map<String, Integer> merchantFrequency = new HashMap<>();
        for (TransactionAnalytics transaction : historical) {
            if (transaction.getMerchantId() != null) {
                merchantFrequency.merge(transaction.getMerchantId().toString(), 1, Integer::sum);
            }
        }
        
        for (TransactionAnalytics transaction : current) {
            if (transaction.getMerchantId() != null) {
                String merchantId = transaction.getMerchantId().toString();
                if (!merchantFrequency.containsKey(merchantId)) {
                    TransactionAnomaly anomaly = TransactionAnomaly.builder()
                            .anomalyId(UUID.randomUUID().toString())
                            .userId(userId)
                            .transactionId(transaction.getTransactionId())
                            .type("NEW_MERCHANT_ANOMALY")
                            .severity("LOW")
                            .description("Transaction with new merchant")
                            .anomalyScore(BigDecimal.valueOf(0.3))
                            .detectedAt(LocalDateTime.now())
                            .metadata(Map.of(
                                    "merchantId", merchantId,
                                    "isNewMerchant", true
                            ))
                            .build();
                    
                    anomalies.add(anomaly);
                }
            }
        }
        
        return anomalies;
    }
    
    // Real-time check methods
    
    private TransactionAnomaly checkAmountAnomaly(TransactionAnalytics transaction, 
            List<TransactionAnalytics> historical) {
        
        List<BigDecimal> amounts = historical.stream()
                .map(TransactionAnalytics::getAmount)
                .collect(Collectors.toList());
        
        if (amounts.isEmpty()) return null;
        
        double mean = calculateMean(amounts);
        double stdDev = calculateStandardDeviation(amounts, mean);
        double zScore = Math.abs((transaction.getAmount().doubleValue() - mean) / stdDev);
        double threshold = DEFAULT_ANOMALY_THRESHOLD * AMOUNT_THRESHOLD_MULTIPLIER;
        
        if (zScore > threshold) {
            return TransactionAnomaly.builder()
                    .anomalyId(UUID.randomUUID().toString())
                    .userId(transaction.getUserId())
                    .transactionId(transaction.getTransactionId())
                    .type("REAL_TIME_AMOUNT_ANOMALY")
                    .severity(calculateSeverity(zScore, DEFAULT_ANOMALY_THRESHOLD))
                    .description(String.format("Real-time amount anomaly detected: $%.2f", 
                            transaction.getAmount()))
                    .anomalyScore(BigDecimal.valueOf(zScore).setScale(2, RoundingMode.HALF_UP))
                    .detectedAt(LocalDateTime.now())
                    .build();
        }
        
        return null;
    }
    
    private TransactionAnomaly checkLocationAnomaly(TransactionAnalytics transaction, 
            List<TransactionAnalytics> historical) {
        
        if (transaction.getLocation() == null) return null;
        
        boolean isNewLocation = historical.stream()
                .noneMatch(t -> transaction.getLocation().equals(t.getLocation()));
        
        if (isNewLocation) {
            return TransactionAnomaly.builder()
                    .anomalyId(UUID.randomUUID().toString())
                    .userId(transaction.getUserId())
                    .transactionId(transaction.getTransactionId())
                    .type("REAL_TIME_LOCATION_ANOMALY")
                    .severity("MEDIUM")
                    .description("Real-time location anomaly: new location detected")
                    .anomalyScore(BigDecimal.valueOf(0.8))
                    .detectedAt(LocalDateTime.now())
                    .build();
        }
        
        return null;
    }
    
    private TransactionAnomaly checkTimeAnomaly(TransactionAnalytics transaction, 
            List<TransactionAnalytics> historical) {
        
        int hour = transaction.getTimestamp().getHour();
        boolean hasHistoricalAtHour = historical.stream()
                .anyMatch(t -> t.getTimestamp().getHour() == hour);
        
        if (!hasHistoricalAtHour && (hour < 6 || hour > 23)) {
            return TransactionAnomaly.builder()
                    .anomalyId(UUID.randomUUID().toString())
                    .userId(transaction.getUserId())
                    .transactionId(transaction.getTransactionId())
                    .type("REAL_TIME_TIME_ANOMALY")
                    .severity("LOW")
                    .description("Real-time time anomaly: unusual transaction time")
                    .anomalyScore(BigDecimal.valueOf(0.4))
                    .detectedAt(LocalDateTime.now())
                    .build();
        }
        
        return null;
    }
    
    // Utility methods
    
    private double calculateMean(List<BigDecimal> values) {
        return values.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);
    }
    
    private double calculateStandardDeviation(List<BigDecimal> values, double mean) {
        if (values.size() <= 1) return 1.0;
        
        double variance = values.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .map(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(1.0);
        
        return Math.sqrt(variance);
    }
    
    private double calculateAmountVariability(List<TransactionAnalytics> transactions) {
        List<BigDecimal> amounts = transactions.stream()
                .map(TransactionAnalytics::getAmount)
                .collect(Collectors.toList());
        
        if (amounts.size() <= 1) return 0.0;
        
        double mean = calculateMean(amounts);
        double stdDev = calculateStandardDeviation(amounts, mean);
        
        return stdDev / mean; // Coefficient of variation
    }
    
    private double calculateFrequencyVariability(List<TransactionAnalytics> transactions) {
        Map<String, Integer> dailyFrequency = new HashMap<>();
        
        for (TransactionAnalytics transaction : transactions) {
            String dayKey = transaction.getTimestamp().toLocalDate().toString();
            dailyFrequency.merge(dayKey, 1, Integer::sum);
        }
        
        if (dailyFrequency.size() <= 1) return 0.0;
        
        List<BigDecimal> frequencies = dailyFrequency.values().stream()
                .map(BigDecimal::valueOf)
                .collect(Collectors.toList());
        
        double mean = calculateMean(frequencies);
        double stdDev = calculateStandardDeviation(frequencies, mean);
        
        return stdDev / mean; // Coefficient of variation
    }
    
    private String calculateSeverity(double zScore, double threshold) {
        if (zScore > threshold * 2.5) return "CRITICAL";
        if (zScore > threshold * 2.0) return "HIGH";
        if (zScore > threshold * 1.5) return "MEDIUM";
        return "LOW";
    }
    
    private int getSeverityWeight(String severity) {
        switch (severity) {
            case "CRITICAL": return 4;
            case "HIGH": return 3;
            case "MEDIUM": return 2;
            case "LOW": return 1;
            default: return 0;
        }
    }
    
    private void publishAnomalyAlert(TransactionAnomaly anomaly) {
        try {
            kafkaTemplate.send("anomaly-alerts", anomaly.getUserId().toString(), anomaly);
            log.info("Published anomaly alert: {} for user: {}", 
                    anomaly.getAnomalyId(), anomaly.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish anomaly alert: {}", anomaly.getAnomalyId(), e);
        }
    }
    
    private void updateSpendingPatterns(UUID userId, List<TransactionAnalytics> transactions) {
        // Update user's spending patterns in cache or database
        // This is a placeholder for ML model updates
        log.debug("Updating spending patterns for user: {} with {} transactions", 
                userId, transactions.size());
    }
    
    private void recalculateBaselineMetrics(UUID userId, List<TransactionAnalytics> transactions) {
        // Recalculate baseline metrics for the user
        // This is a placeholder for statistical baseline updates
        log.debug("Recalculating baseline metrics for user: {}", userId);
    }
}