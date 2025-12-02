package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comprehensive fraud monitoring statistics model for business logic layer.
 * Contains aggregated metrics, calculations, and business rules for fraud analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudMonitoringStatistics {

    /**
     * Unique identifier for this statistics snapshot
     */
    private String statisticsId;

    /**
     * Time period start
     */
    private LocalDateTime periodStart;

    /**
     * Time period end
     */
    private LocalDateTime periodEnd;

    /**
     * Total number of transactions analyzed
     */
    private long totalTransactionsAnalyzed;

    /**
     * Number of transactions flagged as fraud
     */
    private long fraudDetected;

    /**
     * Number of false positive alerts
     */
    private long falsePositives;

    /**
     * Number of true positive alerts (confirmed fraud)
     */
    private long truePositives;

    /**
     * Number of false negatives (missed fraud)
     */
    private long falseNegatives;

    /**
     * Number of true negatives (correctly passed)
     */
    private long trueNegatives;

    /**
     * Average fraud score across all transactions
     */
    private double averageFraudScore;

    /**
     * Average processing time in milliseconds
     */
    private double averageProcessingTime;

    /**
     * Peak processing time in milliseconds
     */
    private double peakProcessingTime;

    /**
     * Number of high risk transactions
     */
    private long highRiskTransactions;

    /**
     * Number of blocked transactions
     */
    private long blockedTransactions;

    /**
     * Number of transactions requiring manual review
     */
    private long manualReviewRequired;

    /**
     * Total alerts generated
     */
    private long totalAlerts;

    /**
     * Alerts by severity level
     */
    private Map<String, Long> alertsBySeverity;

    /**
     * Fraud detection by rule type
     */
    private Map<String, Long> detectionsByRuleType;

    /**
     * ML model accuracy
     */
    private double mlModelAccuracy;

    /**
     * ML model precision
     */
    private double mlModelPrecision;

    /**
     * ML model recall
     */
    private double mlModelRecall;

    /**
     * ML model F1 score
     */
    private double mlModelF1Score;

    /**
     * Total financial impact (amount saved by blocking fraud)
     */
    private double totalAmountBlocked;

    /**
     * Average amount per fraudulent transaction
     */
    private double averageFraudAmount;

    /**
     * Statistics generation timestamp
     */
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();

    /**
     * BUSINESS LOGIC: Calculate detection rate
     */
    public double getDetectionRate() {
        if (totalTransactionsAnalyzed == 0) return 0.0;
        return (double) fraudDetected / totalTransactionsAnalyzed * 100.0;
    }

    /**
     * BUSINESS LOGIC: Calculate precision (positive predictive value)
     */
    public double getPrecision() {
        long totalPositives = truePositives + falsePositives;
        if (totalPositives == 0) return 0.0;
        return (double) truePositives / totalPositives * 100.0;
    }

    /**
     * BUSINESS LOGIC: Calculate recall (sensitivity)
     */
    public double getRecall() {
        long actualFraud = truePositives + falseNegatives;
        if (actualFraud == 0) return 0.0;
        return (double) truePositives / actualFraud * 100.0;
    }

    /**
     * BUSINESS LOGIC: Calculate F1 score
     */
    public double getF1Score() {
        double precision = getPrecision();
        double recall = getRecall();
        if (precision + recall == 0) return 0.0;
        return 2 * (precision * recall) / (precision + recall);
    }

    /**
     * BUSINESS LOGIC: Calculate false positive rate
     */
    public double getFalsePositiveRate() {
        if (totalTransactionsAnalyzed == 0) return 0.0;
        return (double) falsePositives / totalTransactionsAnalyzed * 100.0;
    }

    /**
     * BUSINESS LOGIC: Calculate specificity (true negative rate)
     */
    public double getSpecificity() {
        long actualNonFraud = trueNegatives + falsePositives;
        if (actualNonFraud == 0) return 0.0;
        return (double) trueNegatives / actualNonFraud * 100.0;
    }

    /**
     * BUSINESS LOGIC: Calculate accuracy
     */
    public double getAccuracy() {
        if (totalTransactionsAnalyzed == 0) return 0.0;
        long correct = truePositives + trueNegatives;
        return (double) correct / totalTransactionsAnalyzed * 100.0;
    }

    /**
     * BUSINESS LOGIC: Calculate block rate
     */
    public double getBlockRate() {
        if (totalTransactionsAnalyzed == 0) return 0.0;
        return (double) blockedTransactions / totalTransactionsAnalyzed * 100.0;
    }

    /**
     * BUSINESS LOGIC: Calculate manual review rate
     */
    public double getManualReviewRate() {
        if (totalTransactionsAnalyzed == 0) return 0.0;
        return (double) manualReviewRequired / totalTransactionsAnalyzed * 100.0;
    }

    /**
     * BUSINESS LOGIC: Check if system is performing well
     */
    public boolean isPerformingWell() {
        return getPrecision() >= 70.0
            && getRecall() >= 80.0
            && getFalsePositiveRate() <= 5.0
            && averageProcessingTime <= 500.0; // 500ms threshold
    }

    /**
     * BUSINESS LOGIC: Get performance status
     */
    public PerformanceStatus getPerformanceStatus() {
        if (getPrecision() >= 90.0 && getRecall() >= 90.0 && getFalsePositiveRate() <= 2.0) {
            return PerformanceStatus.EXCELLENT;
        } else if (getPrecision() >= 75.0 && getRecall() >= 75.0 && getFalsePositiveRate() <= 5.0) {
            return PerformanceStatus.GOOD;
        } else if (getPrecision() >= 60.0 && getRecall() >= 60.0 && getFalsePositiveRate() <= 10.0) {
            return PerformanceStatus.ACCEPTABLE;
        } else if (getPrecision() >= 40.0 && getRecall() >= 40.0) {
            return PerformanceStatus.POOR;
        } else {
            return PerformanceStatus.CRITICAL;
        }
    }

    /**
     * Performance status enum
     */
    public enum PerformanceStatus {
        EXCELLENT,
        GOOD,
        ACCEPTABLE,
        POOR,
        CRITICAL
    }

    /**
     * BUSINESS LOGIC: Get summary report
     */
    public String getSummaryReport() {
        return String.format(
            "Fraud Monitoring Statistics Summary:\n" +
            "Period: %s to %s\n" +
            "Transactions Analyzed: %,d\n" +
            "Fraud Detected: %,d (%.2f%%)\n" +
            "Precision: %.2f%%\n" +
            "Recall: %.2f%%\n" +
            "F1 Score: %.2f%%\n" +
            "False Positive Rate: %.2f%%\n" +
            "Accuracy: %.2f%%\n" +
            "Avg Processing Time: %.2f ms\n" +
            "Performance Status: %s\n" +
            "Total Amount Blocked: $%,.2f",
            periodStart, periodEnd,
            totalTransactionsAnalyzed,
            fraudDetected, getDetectionRate(),
            getPrecision(),
            getRecall(),
            getF1Score(),
            getFalsePositiveRate(),
            getAccuracy(),
            averageProcessingTime,
            getPerformanceStatus(),
            totalAmountBlocked
        );
    }
}
