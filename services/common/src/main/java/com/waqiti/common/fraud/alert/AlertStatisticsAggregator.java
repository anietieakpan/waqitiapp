package com.waqiti.common.fraud.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.waqiti.common.fraud.model.AlertLevel;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time alert statistics aggregator for the Waqiti fraud detection system
 * 
 * This component provides comprehensive real-time statistics and metrics
 * for fraud alerts, including performance tracking, accuracy metrics,
 * and operational dashboards.
 * 
 * Features:
 * - Real-time alert counting and categorization
 * - Performance metrics (resolution times, accuracy)
 * - User and merchant-specific statistics
 * - Alert type and severity distribution
 * - Investigation effectiveness tracking
 * - Operational KPIs and SLA monitoring
 */
@Slf4j
@Component
public class AlertStatisticsAggregator {
    
    private final Map<String, AtomicLong> alertCounters = new ConcurrentHashMap<>();
    private final Map<AlertLevel, AtomicLong> levelCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> typeCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> userCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> merchantCounters = new ConcurrentHashMap<>();
    private final List<Double> resolutionTimes = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger truePositives = new AtomicInteger(0);
    private final AtomicInteger falsePositives = new AtomicInteger(0);
    private final AtomicInteger trueNegatives = new AtomicInteger(0);
    private final AtomicInteger falseNegatives = new AtomicInteger(0);
    private final LocalDateTime startTime = LocalDateTime.now();
    
    /**
     * Record new alert
     */
    public void recordAlert(FraudAlert alert) {
        incrementCounter("total");
        incrementCounter("pending");
        
        levelCounters.computeIfAbsent(convertSeverityToLevel(alert.getSeverity()), k -> new AtomicLong(0))
            .incrementAndGet();
        
        typeCounters.computeIfAbsent(alert.getAlertType().name(), k -> new AtomicLong(0))
            .incrementAndGet();
        
        userCounters.computeIfAbsent(alert.getUserId(), k -> new AtomicLong(0))
            .incrementAndGet();
        
        log.debug("Recorded new alert: {} with level: {}", alert.getAlertId(), alert.getSeverity());
    }
    
    /**
     * Record alert resolution
     */
    public void recordResolution(FraudAlert alert, double resolutionTimeMinutes, boolean wasFraud) {
        decrementCounter("pending");
        incrementCounter("resolved");
        
        resolutionTimes.add(resolutionTimeMinutes);
        
        if (wasFraud) {
            truePositives.incrementAndGet();
        } else {
            falsePositives.incrementAndGet();
        }
        
        log.debug("Recorded resolution for alert: {} in {} minutes, was fraud: {}", 
                  alert.getAlertId(), resolutionTimeMinutes, wasFraud);
    }
    
    /**
     * Record missed fraud case
     */
    public void recordMissedFraud() {
        falseNegatives.incrementAndGet();
        log.warn("Recorded missed fraud case");
    }
    
    /**
     * Record correctly identified non-fraud
     */
    public void recordTrueNegative() {
        trueNegatives.incrementAndGet();
    }
    
    /**
     * Get alert count by counter name
     */
    public long getAlertCount(String counterName) {
        return alertCounters.getOrDefault(counterName, new AtomicLong(0)).get();
    }
    
    /**
     * Get alert count by level
     */
    public long getAlertCountByLevel(AlertLevel level) {
        return levelCounters.getOrDefault(level, new AtomicLong(0)).get();
    }
    
    /**
     * Get alert count by type
     */
    public long getAlertCountByType(String alertType) {
        return typeCounters.getOrDefault(alertType, new AtomicLong(0)).get();
    }
    
    /**
     * Get alert count by user
     */
    public long getAlertCountByUser(String userId) {
        return userCounters.getOrDefault(userId, new AtomicLong(0)).get();
    }
    
    /**
     * Calculate false positive rate
     */
    public double getFalsePositiveRate() {
        int total = truePositives.get() + falsePositives.get();
        if (total == 0) return 0.0;
        return (double) falsePositives.get() / total;
    }
    
    /**
     * Calculate precision (positive predictive value)
     */
    public double getPrecision() {
        int positives = truePositives.get() + falsePositives.get();
        if (positives == 0) return 0.0;
        return (double) truePositives.get() / positives;
    }
    
    /**
     * Calculate recall (sensitivity)
     */
    public double getRecall() {
        int actualPositives = truePositives.get() + falseNegatives.get();
        if (actualPositives == 0) return 0.0;
        return (double) truePositives.get() / actualPositives;
    }
    
    /**
     * Calculate F1 score
     */
    public double getF1Score() {
        double precision = getPrecision();
        double recall = getRecall();
        if (precision + recall == 0) return 0.0;
        return 2 * (precision * recall) / (precision + recall);
    }
    
    /**
     * Get average resolution time
     */
    public double getAverageResolutionTime() {
        synchronized (resolutionTimes) {
            if (resolutionTimes.isEmpty()) return 0.0;
            return resolutionTimes.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
        }
    }
    
    /**
     * Get median resolution time
     */
    public double getMedianResolutionTime() {
        synchronized (resolutionTimes) {
            if (resolutionTimes.isEmpty()) return 0.0;
            
            List<Double> sorted = new ArrayList<>(resolutionTimes);
            Collections.sort(sorted);
            
            int size = sorted.size();
            if (size % 2 == 0) {
                return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
            } else {
                return sorted.get(size / 2);
            }
        }
    }
    
    /**
     * Get 95th percentile resolution time
     */
    public double get95thPercentileResolutionTime() {
        synchronized (resolutionTimes) {
            if (resolutionTimes.isEmpty()) return 0.0;
            
            List<Double> sorted = new ArrayList<>(resolutionTimes);
            Collections.sort(sorted);
            
            int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
            return sorted.get(Math.max(0, index));
        }
    }
    
    /**
     * Get top users by alert count
     */
    public Map<String, Long> getTopUsersByAlertCount(int limit) {
        return userCounters.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue(
                        (a, b) -> Long.compare(b.get(), a.get())))
                .limit(limit)
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().get()),
                        LinkedHashMap::putAll);
    }
    
    /**
     * Get alert distribution by level
     */
    public Map<AlertLevel, Long> getAlertDistributionByLevel() {
        Map<AlertLevel, Long> distribution = new EnumMap<>(AlertLevel.class);
        for (AlertLevel level : AlertLevel.values()) {
            distribution.put(level, getAlertCountByLevel(level));
        }
        return distribution;
    }
    
    /**
     * Get alert distribution by type
     */
    public Map<String, Long> getAlertDistributionByType() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        typeCounters.forEach((type, count) -> distribution.put(type, count.get()));
        return distribution;
    }
    
    /**
     * Calculate alerts per hour
     */
    public double getAlertsPerHour() {
        long totalAlerts = getAlertCount("total");
        long hoursElapsed = java.time.Duration.between(startTime, LocalDateTime.now()).toHours();
        if (hoursElapsed == 0) hoursElapsed = 1; // Avoid division by zero
        return (double) totalAlerts / hoursElapsed;
    }
    
    /**
     * Reset all statistics
     */
    public void reset() {
        alertCounters.clear();
        levelCounters.clear();
        typeCounters.clear();
        userCounters.clear();
        merchantCounters.clear();
        resolutionTimes.clear();
        truePositives.set(0);
        falsePositives.set(0);
        trueNegatives.set(0);
        falseNegatives.set(0);
        
        log.info("Alert statistics reset at {}", LocalDateTime.now());
    }
    
    /**
     * Get comprehensive statistics summary
     */
    public AlertStatisticsSummary getSummary() {
        return AlertStatisticsSummary.builder()
                .totalAlerts(getAlertCount("total"))
                .pendingAlerts(getAlertCount("pending"))
                .resolvedAlerts(getAlertCount("resolved"))
                .falsePositiveRate(getFalsePositiveRate())
                .precision(getPrecision())
                .recall(getRecall())
                .f1Score(getF1Score())
                .averageResolutionTime(getAverageResolutionTime())
                .medianResolutionTime(getMedianResolutionTime())
                .p95ResolutionTime(get95thPercentileResolutionTime())
                .alertsPerHour(getAlertsPerHour())
                .alertDistributionByLevel(getAlertDistributionByLevel())
                .alertDistributionByType(getAlertDistributionByType())
                .topUsersByAlertCount(getTopUsersByAlertCount(10))
                .statisticsGeneratedAt(LocalDateTime.now())
                .statisticsStartTime(startTime)
                .build();
    }
    
    // Helper methods
    private void incrementCounter(String counterName) {
        alertCounters.computeIfAbsent(counterName, k -> new AtomicLong(0))
                    .incrementAndGet();
    }
    
    private void decrementCounter(String counterName) {
        alertCounters.computeIfAbsent(counterName, k -> new AtomicLong(0))
                    .decrementAndGet();
    }
    
    /**
     * Convert AlertSeverity to AlertLevel for statistics tracking
     */
    private AlertLevel convertSeverityToLevel(FraudAlert.AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> AlertLevel.CRITICAL;
            case HIGH -> AlertLevel.HIGH;
            case MEDIUM -> AlertLevel.MEDIUM;
            case LOW -> AlertLevel.LOW;
            case INFORMATIONAL -> AlertLevel.INFO;
        };
    }
    
    /**
     * Statistics summary DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertStatisticsSummary {
        private long totalAlerts;
        private long pendingAlerts;
        private long resolvedAlerts;
        private double falsePositiveRate;
        private double precision;
        private double recall;
        private double f1Score;
        private double averageResolutionTime;
        private double medianResolutionTime;
        private double p95ResolutionTime;
        private double alertsPerHour;
        private Map<AlertLevel, Long> alertDistributionByLevel;
        private Map<String, Long> alertDistributionByType;
        private Map<String, Long> topUsersByAlertCount;
        private LocalDateTime statisticsGeneratedAt;
        private LocalDateTime statisticsStartTime;
    }
}