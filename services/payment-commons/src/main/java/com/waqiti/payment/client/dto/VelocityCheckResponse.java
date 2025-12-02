package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Velocity Check Response
 * 
 * Response containing velocity check results and limit violations.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityCheckResponse {
    
    /**
     * Check ID
     */
    private String checkId;
    
    /**
     * Overall check status
     */
    private CheckStatus status;
    
    /**
     * Overall velocity verdict
     */
    private VelocityVerdict verdict;
    
    /**
     * Overall risk score (0.0 to 1.0)
     */
    private Double overallRiskScore;
    
    /**
     * Individual check results
     */
    private List<VelocityCheckResult> checkResults;
    
    /**
     * Violated limits
     */
    private List<LimitViolation> violations;
    
    /**
     * Velocity statistics
     */
    private VelocityStatistics statistics;
    
    /**
     * Recommendations
     */
    private List<VelocityRecommendation> recommendations;
    
    /**
     * Check execution time in milliseconds
     */
    private Long executionTimeMs;
    
    /**
     * Check completed timestamp
     */
    private LocalDateTime checkedAt;
    
    /**
     * Data sources used
     */
    private List<String> dataSourcesUsed;
    
    /**
     * Check metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Alerts generated
     */
    private List<VelocityAlert> alerts;
    
    /**
     * Historical context
     */
    private HistoricalContext historicalContext;
    
    public enum CheckStatus {
        COMPLETED,
        PARTIAL,
        FAILED,
        TIMEOUT
    }
    
    public enum VelocityVerdict {
        ALLOW,
        REVIEW,
        DECLINE,
        BLOCK
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityCheckResult {
        private VelocityCheckRequest.VelocityCheckType checkType;
        private String timeWindow;
        private Boolean limitExceeded;
        private Object currentValue;
        private Object limitValue;
        private Double utilizationPercentage;
        private Double riskScore;
        private String status;
        private Map<String, Object> checkData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitViolation {
        private String violationType;
        private VelocityCheckRequest.VelocityCheckType checkType;
        private String timeWindow;
        private Object actualValue;
        private Object limitValue;
        private Double exceedancePercentage;
        private String severity;
        private String description;
        private LocalDateTime detectedAt;
        private Map<String, Object> violationData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityStatistics {
        private Integer totalTransactions;
        private BigDecimal totalAmount;
        private Integer uniqueMerchants;
        private Integer uniqueDevices;
        private Integer uniqueLocations;
        private Integer failedAttempts;
        private Integer declinedTransactions;
        private Double averageAmount;
        private BigDecimal maxAmount;
        private String timeSpanAnalyzed;
        private Map<String, Object> additionalStats;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityRecommendation {
        private String recommendationType;
        private String description;
        private String priority;
        private String action;
        private Map<String, Object> actionData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityAlert {
        private String alertId;
        private String alertType;
        private String severity;
        private String description;
        private VelocityCheckRequest.VelocityCheckType triggerType;
        private String timeWindow;
        private LocalDateTime triggeredAt;
        private Map<String, Object> alertData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalContext {
        private String trendDirection; // INCREASING, DECREASING, STABLE
        private Double changeRate;
        private String comparisonPeriod;
        private Boolean unusualActivity;
        private List<HistoricalAnomaly> anomalies;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalAnomaly {
        private String anomalyType;
        private String description;
        private LocalDateTime detectedAt;
        private Double severity;
    }
    
    /**
     * Check if any velocity limits were exceeded
     */
    public boolean hasLimitViolations() {
        return violations != null && !violations.isEmpty();
    }
    
    /**
     * Check if transaction should be blocked
     */
    public boolean shouldBlock() {
        return verdict == VelocityVerdict.BLOCK || verdict == VelocityVerdict.DECLINE;
    }
    
    /**
     * Check if manual review is needed
     */
    public boolean needsReview() {
        return verdict == VelocityVerdict.REVIEW ||
               (hasLimitViolations() && getCriticalViolations().size() > 0);
    }
    
    /**
     * Get critical violations
     */
    public List<LimitViolation> getCriticalViolations() {
        if (violations == null) {
            return List.of();
        }
        
        return violations.stream()
                .filter(v -> "CRITICAL".equals(v.getSeverity()) || "HIGH".equals(v.getSeverity()))
                .toList();
    }
    
    /**
     * Get critical alerts
     */
    public List<VelocityAlert> getCriticalAlerts() {
        if (alerts == null) {
            return List.of();
        }
        
        return alerts.stream()
                .filter(a -> "CRITICAL".equals(a.getSeverity()))
                .toList();
    }
    
    /**
     * Get highest risk check result
     */
    public VelocityCheckResult getHighestRiskResult() {
        if (checkResults == null || checkResults.isEmpty()) {
            return null;
        }
        
        return checkResults.stream()
                .filter(r -> r.getRiskScore() != null)
                .max((r1, r2) -> Double.compare(r1.getRiskScore(), r2.getRiskScore()))
                .orElse(null);
    }
    
    /**
     * Get velocity summary
     */
    public String getVelocitySummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Verdict: ").append(verdict);
        
        if (hasLimitViolations()) {
            summary.append(", ").append(violations.size()).append(" violations");
        }
        
        if (overallRiskScore != null) {
            summary.append(", Risk: ").append(String.format("%.2f", overallRiskScore));
        }
        
        return summary.toString();
    }
    
    /**
     * Check if velocity is trending upward
     */
    public boolean isTrendingUp() {
        return historicalContext != null &&
               "INCREASING".equals(historicalContext.getTrendDirection());
    }
    
    /**
     * Check if there's unusual activity
     */
    public boolean hasUnusualActivity() {
        return historicalContext != null &&
               historicalContext.getUnusualActivity() != null &&
               historicalContext.getUnusualActivity();
    }
    
    /**
     * Get execution time in seconds
     */
    public Double getExecutionTimeSeconds() {
        return executionTimeMs != null ? executionTimeMs / 1000.0 : null;
    }
    
    /**
     * Check if all checks completed successfully
     */
    public boolean isFullyCompleted() {
        return status == CheckStatus.COMPLETED;
    }
    
    /**
     * Get utilization percentage for specific check type
     */
    public Double getUtilizationPercentage(VelocityCheckRequest.VelocityCheckType checkType) {
        if (checkResults == null) {
            return null;
        }
        
        return checkResults.stream()
                .filter(r -> r.getCheckType() == checkType)
                .mapToDouble(r -> r.getUtilizationPercentage() != null ? r.getUtilizationPercentage() : 0.0)
                .max()
                .orElse(0.0);
    }
}