package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fraud alert statistics and analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertStatistics {

    @JsonProperty("period_start")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodStart;

    @JsonProperty("period_end")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodEnd;

    @JsonProperty("total_alerts")
    private Long totalAlerts;

    @JsonProperty("active_alerts")
    private Long activeAlerts;

    @JsonProperty("resolved_alerts")
    private Long resolvedAlerts;

    @JsonProperty("false_positives")
    private Long falsePositives;

    @JsonProperty("true_positives")
    private Long truePositives;

    @JsonProperty("alerts_by_type")
    private Map<String, Long> alertsByType;

    @JsonProperty("alerts_by_severity")
    private Map<String, Long> alertsBySeverity;

    @JsonProperty("alerts_by_status")
    private Map<String, Long> alertsByStatus;

    @JsonProperty("alerts_by_hour")
    private Map<Integer, Long> alertsByHour;

    @JsonProperty("alerts_by_day")
    private Map<String, Long> alertsByDay;

    @JsonProperty("average_resolution_time_minutes")
    private Double averageResolutionTimeMinutes;

    @JsonProperty("median_resolution_time_minutes")
    private Double medianResolutionTimeMinutes;

    @JsonProperty("sla_compliance_rate")
    private BigDecimal slaComplianceRate;

    @JsonProperty("false_positive_rate")
    private BigDecimal falsePositiveRate;

    @JsonProperty("detection_accuracy")
    private BigDecimal detectionAccuracy;

    @JsonProperty("total_prevented_loss")
    private BigDecimal totalPreventedLoss;

    @JsonProperty("total_actual_loss")
    private BigDecimal totalActualLoss;

    @JsonProperty("critical_alerts")
    private Long criticalAlerts;

    @JsonProperty("high_priority_alerts")
    private Long highPriorityAlerts;

    @JsonProperty("escalated_alerts")
    private Long escalatedAlerts;

    @JsonProperty("auto_resolved_alerts")
    private Long autoResolvedAlerts;

    @JsonProperty("manually_resolved_alerts")
    private Long manuallyResolvedAlerts;

    @JsonProperty("top_alert_reasons")
    private Map<String, Long> topAlertReasons;

    @JsonProperty("top_affected_users")
    private Map<String, Long> topAffectedUsers;

    @JsonProperty("top_affected_merchants")
    private Map<String, Long> topAffectedMerchants;

    @JsonProperty("alert_response_times")
    private Map<String, Double> alertResponseTimes;

    @JsonProperty("investigation_outcomes")
    private Map<String, Long> investigationOutcomes;

    @JsonProperty("generated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime generatedAt;

    /**
     * Calculate alert effectiveness score
     */
    public BigDecimal calculateEffectivenessScore() {
        if (totalAlerts == null || totalAlerts == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal accuracyWeight = new BigDecimal("0.4");
        BigDecimal slaWeight = new BigDecimal("0.3");
        BigDecimal resolutionWeight = new BigDecimal("0.3");
        
        BigDecimal score = BigDecimal.ZERO;
        
        if (detectionAccuracy != null) {
            score = score.add(detectionAccuracy.multiply(accuracyWeight));
        }
        
        if (slaComplianceRate != null) {
            score = score.add(slaComplianceRate.multiply(slaWeight));
        }
        
        if (averageResolutionTimeMinutes != null) {
            // Normalize resolution time (faster is better)
            BigDecimal resolutionScore = BigDecimal.valueOf(Math.max(0, 100 - averageResolutionTimeMinutes / 10));
            score = score.add(resolutionScore.multiply(resolutionWeight));
        }
        
        return score;
    }

    /**
     * Check if statistics indicate issues
     */
    public boolean hasIssues() {
        return (falsePositiveRate != null && falsePositiveRate.compareTo(new BigDecimal("30")) > 0) ||
               (slaComplianceRate != null && slaComplianceRate.compareTo(new BigDecimal("80")) < 0) ||
               (averageResolutionTimeMinutes != null && averageResolutionTimeMinutes > 240);
    }

    /**
     * Get prevention rate
     */
    public BigDecimal getPreventionRate() {
        if (totalPreventedLoss == null || totalActualLoss == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalPotentialLoss = totalPreventedLoss.add(totalActualLoss);
        if (totalPotentialLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalPreventedLoss.divide(totalPotentialLoss, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));
    }

    /**
     * Generate statistics summary
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Fraud Alert Statistics\n");
        summary.append("======================\n");
        summary.append(String.format("Period: %s to %s\n", periodStart, periodEnd));
        summary.append(String.format("Total Alerts: %d\n", totalAlerts));
        summary.append(String.format("Active: %d | Resolved: %d\n", activeAlerts, resolvedAlerts));
        summary.append(String.format("True Positives: %d | False Positives: %d\n", truePositives, falsePositives));
        summary.append(String.format("Detection Accuracy: %.2f%%\n", detectionAccuracy));
        summary.append(String.format("False Positive Rate: %.2f%%\n", falsePositiveRate));
        summary.append(String.format("SLA Compliance: %.2f%%\n", slaComplianceRate));
        summary.append(String.format("Avg Resolution Time: %.2f minutes\n", averageResolutionTimeMinutes));
        summary.append(String.format("Prevented Loss: %s\n", totalPreventedLoss));
        summary.append(String.format("Actual Loss: %s\n", totalActualLoss));
        summary.append(String.format("Prevention Rate: %.2f%%\n", getPreventionRate()));
        summary.append(String.format("Effectiveness Score: %.2f/100\n", calculateEffectivenessScore()));
        
        if (hasIssues()) {
            summary.append("\n⚠️ Issues Detected:\n");
            if (falsePositiveRate != null && falsePositiveRate.compareTo(new BigDecimal("30")) > 0) {
                summary.append("- High false positive rate\n");
            }
            if (slaComplianceRate != null && slaComplianceRate.compareTo(new BigDecimal("80")) < 0) {
                summary.append("- Low SLA compliance\n");
            }
            if (averageResolutionTimeMinutes != null && averageResolutionTimeMinutes > 240) {
                summary.append("- High average resolution time\n");
            }
        }
        
        return summary.toString();
    }
}