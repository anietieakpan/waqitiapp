package com.waqiti.common.fraud.alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waqiti.common.fraud.model.AlertLevel;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fraud alert statistics and metrics tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class FraudAlertStatistics {
    
    private String statisticsId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime lastUpdated;
    private long totalAlerts;
    private long resolvedAlerts;
    private long pendingAlerts;
    private int totalPendingAlerts;
    private long escalatedAlerts;
    private int criticalAlerts;
    private int highAlerts;
    private int mediumAlerts;
    private Map<AlertLevel, Long> alertsByLevel;
    private Map<String, Long> alertsByType;
    private Map<String, Long> alertsByUser;
    private Map<String, Long> alertsByMerchant;
    private double averageResolutionTimeMinutes;
    private double falsePositiveRate;
    private double truePositiveRate;
    private double precision;
    private double recall;
    private double f1Score;
    private List<AlertTrend> trends;
    private Map<String, Object> metadata;
    
    /**
     * Calculate key performance indicators
     */
    public AlertKPIs calculateKPIs() {
        return AlertKPIs.builder()
            .totalAlerts(totalAlerts)
            .resolutionRate(totalAlerts > 0 ? (double) resolvedAlerts / totalAlerts : 0.0)
            .escalationRate(totalAlerts > 0 ? (double) escalatedAlerts / totalAlerts : 0.0)
            .pendingRate(totalAlerts > 0 ? (double) pendingAlerts / totalAlerts : 0.0)
            .averageResolutionTime(averageResolutionTimeMinutes)
            .falsePositiveRate(falsePositiveRate)
            .truePositiveRate(truePositiveRate)
            .precision(precision)
            .recall(recall)
            .f1Score(f1Score)
            .criticalAlertRate(getCriticalAlertRate())
            .build();
    }
    
    /**
     * Get critical alert rate
     */
    private double getCriticalAlertRate() {
        if (alertsByLevel == null || totalAlerts == 0) return 0.0;

        // CRITICAL is the highest alert level in canonical AlertLevel enum
        long criticalCount = alertsByLevel.getOrDefault(AlertLevel.CRITICAL, 0L);

        return (double) criticalCount / totalAlerts;
    }
    
    /**
     * Get top alert types
     */
    public List<Map.Entry<String, Long>> getTopAlertTypes(int limit) {
        if (alertsByType == null) return Collections.emptyList();
        
        return alertsByType.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Get top users by alerts
     */
    public List<Map.Entry<String, Long>> getTopUsersByAlerts(int limit) {
        if (alertsByUser == null) return Collections.emptyList();
        
        return alertsByUser.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Get alert distribution by level
     */
    public Map<AlertLevel, Double> getAlertDistribution() {
        if (alertsByLevel == null || totalAlerts == 0) {
            return Collections.emptyMap();
        }
        
        Map<AlertLevel, Double> distribution = new HashMap<>();
        for (Map.Entry<AlertLevel, Long> entry : alertsByLevel.entrySet()) {
            distribution.put(entry.getKey(), (double) entry.getValue() / totalAlerts);
        }
        
        return distribution;
    }
}

