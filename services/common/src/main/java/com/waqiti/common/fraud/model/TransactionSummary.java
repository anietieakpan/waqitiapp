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
 * Transaction summary for fraud monitoring and reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("period_start")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodStart;

    @JsonProperty("period_end")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime periodEnd;

    @JsonProperty("total_transactions")
    private Long totalTransactions;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("average_amount")
    private BigDecimal averageAmount;

    @JsonProperty("max_amount")
    private BigDecimal maxAmount;

    @JsonProperty("min_amount")
    private BigDecimal minAmount;

    @JsonProperty("successful_transactions")
    private Long successfulTransactions;

    @JsonProperty("failed_transactions")
    private Long failedTransactions;

    @JsonProperty("disputed_transactions")
    private Long disputedTransactions;

    @JsonProperty("refunded_transactions")
    private Long refundedTransactions;

    @JsonProperty("fraud_flagged_transactions")
    private Long fraudFlaggedTransactions;

    @JsonProperty("unique_merchants")
    private Integer uniqueMerchants;

    @JsonProperty("unique_countries")
    private Integer uniqueCountries;

    @JsonProperty("unique_devices")
    private Integer uniqueDevices;

    @JsonProperty("transaction_types")
    private Map<String, Long> transactionTypes;

    @JsonProperty("merchant_categories")
    private Map<String, Long> merchantCategories;

    @JsonProperty("country_breakdown")
    private Map<String, Long> countryBreakdown;

    @JsonProperty("channel_breakdown")
    private Map<String, Long> channelBreakdown;

    @JsonProperty("hourly_distribution")
    private Map<Integer, Long> hourlyDistribution;

    @JsonProperty("daily_distribution")
    private Map<String, Long> dailyDistribution;

    @JsonProperty("risk_score_average")
    private BigDecimal riskScoreAverage;

    @JsonProperty("high_risk_transactions")
    private Long highRiskTransactions;

    @JsonProperty("velocity_breaches")
    private Integer velocityBreaches;

    @JsonProperty("pattern_anomalies")
    private Integer patternAnomalies;

    @JsonProperty("cross_border_percentage")
    private BigDecimal crossBorderPercentage;

    @JsonProperty("night_transaction_percentage")
    private BigDecimal nightTransactionPercentage;

    @JsonProperty("weekend_transaction_percentage")
    private BigDecimal weekendTransactionPercentage;

    @JsonProperty("summary_generated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime summaryGeneratedAt;

    /**
     * Calculate transaction success rate
     */
    public BigDecimal getSuccessRate() {
        if (totalTransactions == null || totalTransactions == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(successfulTransactions)
            .divide(new BigDecimal(totalTransactions), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Calculate fraud rate
     */
    public BigDecimal getFraudRate() {
        if (totalTransactions == null || totalTransactions == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(fraudFlaggedTransactions)
            .divide(new BigDecimal(totalTransactions), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Check if summary indicates suspicious activity
     */
    public boolean indicatesSuspiciousActivity() {
        BigDecimal fraudRate = getFraudRate();
        return fraudRate.compareTo(new BigDecimal("5")) > 0 ||
               velocityBreaches > 5 ||
               patternAnomalies > 10 ||
               (highRiskTransactions != null && highRiskTransactions > totalTransactions / 10);
    }

    /**
     * Get amount (returns average amount for backward compatibility)
     */
    public BigDecimal getAmount() {
        return averageAmount != null ? averageAmount : BigDecimal.ZERO;
    }

    /**
     * Generate summary report
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("Transaction Summary Report\n");
        report.append("==========================\n");
        report.append(String.format("User ID: %s\n", userId));
        report.append(String.format("Period: %s to %s\n", periodStart, periodEnd));
        report.append(String.format("Total Transactions: %d\n", totalTransactions));
        report.append(String.format("Total Amount: %s\n", totalAmount));
        report.append(String.format("Average Amount: %s\n", averageAmount));
        report.append(String.format("Success Rate: %.2f%%\n", getSuccessRate()));
        report.append(String.format("Fraud Rate: %.2f%%\n", getFraudRate()));
        
        if (indicatesSuspiciousActivity()) {
            report.append("\n⚠️ SUSPICIOUS ACTIVITY DETECTED\n");
            report.append(String.format("- High Risk Transactions: %d\n", highRiskTransactions));
            report.append(String.format("- Velocity Breaches: %d\n", velocityBreaches));
            report.append(String.format("- Pattern Anomalies: %d\n", patternAnomalies));
        }
        
        return report.toString();
    }
}