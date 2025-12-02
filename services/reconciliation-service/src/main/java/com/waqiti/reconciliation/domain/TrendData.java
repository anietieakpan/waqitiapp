package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TrendData {
    private final String metricName;
    private final String timeFrame;
    private final List<DataPoint> dataPoints;
    private final BigDecimal trendDirection; // Positive = increasing, Negative = decreasing
    private final BigDecimal trendStrength; // 0-1 scale
    private final LocalDateTime analysisDate;
    
    @Data
    @Builder
    public static class DataPoint {
        private final LocalDateTime timestamp;
        private final BigDecimal value;
        private final String label;
    }
    
    public String getTrendDescription() {
        if (trendDirection == null) {
            return "No trend";
        }
        
        if (trendDirection.compareTo(BigDecimal.ZERO) > 0) {
            return trendStrength.compareTo(BigDecimal.valueOf(0.5)) > 0 ? 
                "Strong upward trend" : "Moderate upward trend";
        } else if (trendDirection.compareTo(BigDecimal.ZERO) < 0) {
            return trendStrength.compareTo(BigDecimal.valueOf(0.5)) > 0 ? 
                "Strong downward trend" : "Moderate downward trend";
        }
        
        return "Stable";
    }
}