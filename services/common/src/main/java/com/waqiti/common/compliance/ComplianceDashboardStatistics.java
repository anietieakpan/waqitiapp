package com.waqiti.common.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Compliance dashboard statistics and metrics
 * 
 * Provides aggregated metrics and statistics for compliance reporting dashboards,
 * executive summaries, and regulatory oversight.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceDashboardStatistics {

    /**
     * Statistics generation timestamp
     */
    private LocalDateTime generatedAt;

    /**
     * Time period for statistics
     */
    private StatisticsPeriod period;

    /**
     * Report statistics
     */
    private ReportStatistics reportStats;

    /**
     * Filing statistics
     */
    private FilingStatistics filingStats;

    /**
     * Alert statistics
     */
    private AlertStatistics alertStats;

    /**
     * Transaction monitoring statistics
     */
    private TransactionMonitoringStats transactionStats;

    /**
     * Customer risk statistics
     */
    private CustomerRiskStats customerStats;

    /**
     * Regulatory compliance metrics
     */
    private RegulatoryMetrics regulatoryMetrics;

    /**
     * Operational metrics
     */
    private OperationalMetrics operationalMetrics;

    /**
     * Trend analysis
     */
    private TrendAnalysis trendAnalysis;

    /**
     * Performance indicators
     */
    private PerformanceIndicators performanceIndicators;

    /**
     * Risk indicators
     */
    private RiskIndicators riskIndicators;

    /**
     * Statistics time period
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsPeriod {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String periodType; // Daily, Weekly, Monthly, Quarterly, Yearly
        private String periodName; // e.g., "Q1 2024", "March 2024"
        private int businessDays;
        private int calendarDays;
    }

    /**
     * Report statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportStatistics {
        private int totalReports;
        private int newReports;
        private int completedReports;
        private int pendingReports;
        private int overdueReports;
        private Map<ComplianceReportType, Integer> reportsByType;
        private Map<ComplianceReportStatus, Integer> reportsByStatus;
        private Map<ComplianceReport.ReportPriority, Integer> reportsByPriority;
        private double averageCompletionTime;
        private double averageQualityScore;
        private int reportsWithIssues;
        private int reportsRequiringAction;
    }

    /**
     * Filing statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilingStatistics {
        private int totalFilings;
        private int successfulFilings;
        private int failedFilings;
        private int rejectedFilings;
        private Map<String, Integer> filingsByAuthority;
        private Map<String, Integer> filingsByJurisdiction;
        private double successRate;
        private double averageFilingTime;
        private int filingsWithinDeadline;
        private int lateFilings;
        private List<FilingTrend> filingTrends;
    }

    /**
     * Alert statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertStatistics {
        private int totalAlerts;
        private int highPriorityAlerts;
        private int unresolvedAlerts;
        private int escalatedAlerts;
        private Map<String, Integer> alertsByType;
        private Map<String, Integer> alertsBySeverity;
        private double averageResolutionTime;
        private double falsePositiveRate;
        private int alertsConvertedToSAR;
        private int alertsConvertedToCTR;
        private List<AlertTrend> alertTrends;
    }

    /**
     * Transaction monitoring statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionMonitoringStats {
        private long totalTransactions;
        private long monitoredTransactions;
        private long flaggedTransactions;
        private BigDecimal totalTransactionValue;
        private BigDecimal flaggedTransactionValue;
        private Map<String, Long> transactionsByType;
        private Map<String, Long> transactionsByCurrency;
        private double suspiciousTransactionRate;
        private double averageTransactionAmount;
        private int uniqueCustomers;
        private int uniqueMerchants;
        private List<TransactionPattern> topPatterns;
    }

    /**
     * Customer risk statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRiskStats {
        private int totalCustomers;
        private int highRiskCustomers;
        private int mediumRiskCustomers;
        private int lowRiskCustomers;
        private int pepsScreened; // Politically Exposed Persons
        private int sanctionsScreened;
        private int adverseMediaHits;
        private Map<String, Integer> customersByRiskCategory;
        private Map<String, Integer> customersByJurisdiction;
        private double averageRiskScore;
        private int enhancedDueDiligenceRequired;
        private List<RiskDistribution> riskDistributions;
    }

    /**
     * Regulatory compliance metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatoryMetrics {
        private double overallComplianceScore;
        private Map<String, Double> complianceByRegulation;
        private int regulatoryExamFindings;
        private int openRemediationItems;
        private int completedRemediationItems;
        private LocalDateTime lastRegulatoryExam;
        private LocalDateTime nextRegulatoryExam;
        private List<RegulatoryDeadline> upcomingDeadlines;
        private Map<String, ComplianceStatus> jurisdictionCompliance;
        private int regulatoryBreaches;
        private BigDecimal totalFines;
    }

    /**
     * Operational metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationalMetrics {
        private double systemUptime;
        private double averageResponseTime;
        private int activeUsers;
        private int processedReports;
        private double automationRate;
        private int manualInterventions;
        private double dataQualityScore;
        private int integrationErrors;
        private Map<String, Integer> tasksByStatus;
        private double resourceUtilization;
        private List<SystemPerformance> performanceMetrics;
    }

    /**
     * Trend analysis
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendAnalysis {
        private List<ReportTrend> reportTrends;
        private List<AlertTrend> alertTrends;
        private List<FilingTrend> filingTrends;
        private List<RiskTrend> riskTrends;
        private String trendSummary;
        private List<String> keyInsights;
        private Map<String, Double> percentageChanges;
        private PredictedTrends predictions;
    }

    /**
     * Performance indicators
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceIndicators {
        private double reportCompletionRate;
        private double filingSuccessRate;
        private double alertResolutionRate;
        private double averageProcessingTime;
        private double dataAccuracyRate;
        private double customerSatisfactionScore;
        private double regulatoryComplianceRate;
        private double operationalEfficiency;
        private Map<String, KPI> keyPerformanceIndicators;
        private PerformanceRating overallRating;
    }

    /**
     * Risk indicators
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskIndicators {
        private double overallRiskScore;
        private String riskLevel;
        private Map<String, Double> riskByCategory;
        private int criticalRisks;
        private int highRisks;
        private int mediumRisks;
        private int lowRisks;
        private List<RiskFactor> topRiskFactors;
        private double riskTrend; // Positive = increasing, Negative = decreasing
        private String riskOutlook;
        private List<RiskMitigation> mitigationActions;
    }

    // Supporting classes

    /**
     * Filing trend
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilingTrend {
        private LocalDateTime date;
        private int filingCount;
        private double successRate;
        private String trendDirection;
    }

    /**
     * Alert trend
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertTrend {
        private LocalDateTime date;
        private int alertCount;
        private String alertType;
        private String trendDirection;
    }

    /**
     * Report trend
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTrend {
        private LocalDateTime date;
        private int reportCount;
        private String reportType;
        private double completionRate;
    }

    /**
     * Risk trend
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskTrend {
        private LocalDateTime date;
        private double riskScore;
        private String riskCategory;
        private String trendDirection;
    }

    /**
     * Transaction pattern
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionPattern {
        private String patternType;
        private int occurrences;
        private BigDecimal totalAmount;
        private String riskLevel;
    }

    /**
     * Risk distribution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskDistribution {
        private String category;
        private int count;
        private double percentage;
        private String trend;
    }

    /**
     * Regulatory deadline
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatoryDeadline {
        private String regulation;
        private LocalDateTime deadline;
        private String description;
        private String status;
        private int daysRemaining;
    }

    /**
     * Compliance status
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceStatus {
        private String jurisdiction;
        private double complianceScore;
        private String status;
        private List<String> issues;
    }

    /**
     * System performance
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemPerformance {
        private String metric;
        private double value;
        private String unit;
        private String status;
    }

    /**
     * Predicted trends
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictedTrends {
        private int expectedReports;
        private int expectedAlerts;
        private double predictedRiskScore;
        private String confidenceLevel;
    }

    /**
     * Key Performance Indicator
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KPI {
        private String name;
        private double value;
        private double target;
        private String status;
        private double percentageToTarget;
    }

    /**
     * Performance rating
     */
    public enum PerformanceRating {
        EXCELLENT, GOOD, SATISFACTORY, NEEDS_IMPROVEMENT, POOR
    }

    /**
     * Risk factor
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorName;
        private double impact;
        private double likelihood;
        private double riskScore;
        private String category;
    }

    /**
     * Risk mitigation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskMitigation {
        private String action;
        private String priority;
        private String owner;
        private LocalDateTime dueDate;
        private String status;
    }

    // Utility methods

    /**
     * Calculate overall health score
     */
    public double calculateOverallHealthScore() {
        double score = 0.0;
        int components = 0;

        if (reportStats != null && reportStats.getTotalReports() > 0) {
            double reportHealth = (double)(reportStats.getTotalReports() - reportStats.getOverdueReports()) / 
                                reportStats.getTotalReports();
            score += reportHealth;
            components++;
        }

        if (filingStats != null) {
            score += filingStats.getSuccessRate();
            components++;
        }

        if (regulatoryMetrics != null) {
            score += regulatoryMetrics.getOverallComplianceScore();
            components++;
        }

        if (performanceIndicators != null) {
            score += performanceIndicators.getOperationalEfficiency();
            components++;
        }

        return components > 0 ? score / components : 0.0;
    }

    /**
     * Get critical issues count
     */
    public int getCriticalIssuesCount() {
        int count = 0;

        if (reportStats != null) {
            count += reportStats.getOverdueReports();
        }

        if (filingStats != null) {
            count += filingStats.getFailedFilings();
        }

        if (alertStats != null) {
            count += alertStats.getHighPriorityAlerts();
        }

        if (riskIndicators != null) {
            count += riskIndicators.getCriticalRisks();
        }

        return count;
    }

    /**
     * Get compliance status summary
     */
    public String getComplianceStatusSummary() {
        double healthScore = calculateOverallHealthScore();
        
        if (healthScore >= 0.9) return "Excellent";
        if (healthScore >= 0.8) return "Good";
        if (healthScore >= 0.7) return "Satisfactory";
        if (healthScore >= 0.6) return "Needs Improvement";
        return "Critical";
    }

    /**
     * Check if immediate action required
     */
    public boolean requiresImmediateAction() {
        return getCriticalIssuesCount() > 0 ||
               (reportStats != null && reportStats.getOverdueReports() > 0) ||
               (riskIndicators != null && "CRITICAL".equals(riskIndicators.getRiskLevel()));
    }
}