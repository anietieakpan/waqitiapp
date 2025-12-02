package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comprehensive uptime and availability metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UptimeMetrics {
    private Duration uptime;
    private Double availability;
    private Duration mttr; // Mean time to recovery
    private Duration mtbf; // Mean time between failures
    private Double currentUptime;
    private Double dailyUptime;
    private Double monthlyUptime;
    private Double yearlyUptime;
    private Long totalOutages;
    private Long plannedMaintenances;
    private LocalDateTime lastOutage;
    private Map<String, Double> uptimeByService;
    
    // Additional comprehensive metrics
    private Long totalIncidents;
    private Long criticalIncidents;
    private Duration longestOutage;
    private Duration shortestOutage;
    private Double slaCompliance;
    private Map<String, Duration> downtimeByCategory;
    private Map<String, Long> incidentsByCategory;
    
    /**
     * Calculate SLA breach status
     */
    public boolean isSlaBreached(double slaTarget) {
        return availability != null && availability < slaTarget;
    }
    
    /**
     * Get uptime percentage as string
     */
    public String getUptimePercentageString() {
        return availability != null ? String.format("%.3f%%", availability) : "N/A";
    }
    
    /**
     * Calculate availability trend (positive = improving, negative = degrading)
     */
    public double getAvailabilityTrend() {
        if (monthlyUptime == null || yearlyUptime == null) {
            return 0.0;
        }
        return monthlyUptime - yearlyUptime;
    }
    
    /**
     * Check if system is currently experiencing an outage
     */
    public boolean isCurrentlyDown() {
        return currentUptime != null && currentUptime < 100.0;
    }
    
    /**
     * Get total downtime in the specified period
     */
    public Duration getTotalDowntime(Duration period) {
        if (availability == null) return Duration.ZERO;
        long totalSeconds = period.getSeconds();
        long downtimeSeconds = (long) (totalSeconds * (1 - availability / 100.0));
        return Duration.ofSeconds(downtimeSeconds);
    }
    
    /**
     * Calculate mean time to detection (MTTD)
     */
    public Duration getMeanTimeToDetection() {
        // This would be calculated from incident data
        return mttr != null ? mttr.dividedBy(2) : Duration.ZERO;
    }
    
    /**
     * Get uptime status based on availability
     */
    public UptimeStatus getUptimeStatus() {
        if (availability == null) return UptimeStatus.UNKNOWN;
        if (availability >= 99.9) return UptimeStatus.EXCELLENT;
        if (availability >= 99.5) return UptimeStatus.GOOD;
        if (availability >= 99.0) return UptimeStatus.ACCEPTABLE;
        if (availability >= 95.0) return UptimeStatus.POOR;
        return UptimeStatus.CRITICAL;
    }
    
    /**
     * Uptime status categories
     */
    public enum UptimeStatus {
        EXCELLENT("99.9%+", "green"),
        GOOD("99.5%+", "lightgreen"),
        ACCEPTABLE("99.0%+", "yellow"),
        POOR("95.0%+", "orange"),
        CRITICAL("<95%", "red"),
        UNKNOWN("N/A", "gray");
        
        private final String threshold;
        private final String color;
        
        UptimeStatus(String threshold, String color) {
            this.threshold = threshold;
            this.color = color;
        }
        
        public String getThreshold() { return threshold; }
        public String getColor() { return color; }
    }
}