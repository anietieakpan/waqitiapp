package com.waqiti.common.fraud.profiling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Behavioral risk analysis data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralRiskData {
    private String userId;
    private double spendingPatternRisk;
    private double timingPatternRisk;
    private double locationPatternRisk;
    private double merchantPatternRisk;
    private double devicePatternRisk;
    private LocalDateTime analysisDate;
    private Map<String, Object> behaviorMetrics;
    private Integer loginFrequency;
    private Integer transactionFrequency;
    private Double averageTransactionAmount;
    private List<Object> anomalousPatterns;
    private List<Integer> activeHours;
    private List<String> knownLocations;
    private List<String> knownDevices;
    private List<Object> deviceFingerprints;
    private Map<Object, Object> locationPatterns;
    private Map<Object, Object> timePatterns;

    public double calculateRiskScore() {
        return (spendingPatternRisk + timingPatternRisk + locationPatternRisk +
                merchantPatternRisk + devicePatternRisk) / 5.0;
    }

    /**
     * Get active hours (convenience getter)
     */
    public List<Integer> getActiveHours() {
        if (activeHours != null) {
            return activeHours;
        }
        if (behaviorMetrics != null && behaviorMetrics.containsKey("activeHours")) {
            Object value = behaviorMetrics.get("activeHours");
            if (value instanceof List) {
                return (List<Integer>) value;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Get known locations (convenience getter)
     */
    public List<String> getKnownLocations() {
        if (knownLocations != null) {
            return knownLocations;
        }
        if (behaviorMetrics != null && behaviorMetrics.containsKey("knownLocations")) {
            Object value = behaviorMetrics.get("knownLocations");
            if (value instanceof List) {
                return (List<String>) value;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Get known devices (convenience getter)
     */
    public List<String> getKnownDevices() {
        if (knownDevices != null) {
            return knownDevices;
        }
        if (behaviorMetrics != null && behaviorMetrics.containsKey("knownDevices")) {
            Object value = behaviorMetrics.get("knownDevices");
            if (value instanceof List) {
                return (List<String>) value;
            }
        }
        return Collections.emptyList();
    }
}
