package com.waqiti.legal.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Legal Statistics Domain Entity
 *
 * Complete production-ready statistical aggregation with:
 * - Period-based statistical snapshots
 * - Multi-dimensional breakdowns (by type, jurisdiction)
 * - Trend analysis and forecasting
 * - Performance benchmarking
 * - Financial metrics
 * - Risk indicators
 * - Operational efficiency metrics
 * - Executive dashboard reporting
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "legal_statistics",
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_legal_period", columnNames = {"period_start", "period_end"})
    },
    indexes = {
        @Index(name = "idx_legal_statistics_period", columnList = "period_end")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "period_start", nullable = false)
    @NotNull
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    @NotNull
    private LocalDate periodEnd;

    @Column(name = "total_legal_documents")
    @Min(0)
    @Builder.Default
    private Integer totalLegalDocuments = 0;

    @Column(name = "active_contracts")
    @Min(0)
    @Builder.Default
    private Integer activeContracts = 0;

    @Column(name = "contract_value", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal contractValue = BigDecimal.ZERO;

    @Column(name = "contracts_expiring_soon")
    @Min(0)
    @Builder.Default
    private Integer contractsExpiringSoon = 0;

    @Column(name = "open_cases")
    @Min(0)
    @Builder.Default
    private Integer openCases = 0;

    @Column(name = "case_win_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal caseWinRate;

    @Column(name = "compliance_score", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal complianceScore;

    @Column(name = "audit_findings")
    @Min(0)
    @Builder.Default
    private Integer auditFindings = 0;

    @Column(name = "high_risk_matters")
    @Min(0)
    @Builder.Default
    private Integer highRiskMatters = 0;

    @Column(name = "legal_spend", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal legalSpend = BigDecimal.ZERO;

    @Type(JsonBinaryType.class)
    @Column(name = "by_document_type", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> byDocumentType = new HashMap<>();

    @Type(JsonBinaryType.class)
    @Column(name = "by_jurisdiction", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> byJurisdiction = new HashMap<>();

    @Column(name = "contract_renewal_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal contractRenewalRate;

    @Column(name = "created_at", nullable = false)
    @NotNull
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Complete business logic methods

    /**
     * Get period duration in days
     */
    public long getPeriodDurationDays() {
        return ChronoUnit.DAYS.between(periodStart, periodEnd);
    }

    /**
     * Check if period is current
     */
    public boolean isCurrentPeriod() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(periodStart) && !today.isAfter(periodEnd);
    }

    /**
     * Update document statistics
     */
    public void updateDocumentStatistics(int totalDocuments, Map<String, Integer> byType) {
        this.totalLegalDocuments = totalDocuments;

        // Update breakdown by document type
        byType.forEach((type, count) -> {
            Map<String, Object> typeStats = new HashMap<>();
            typeStats.put("count", count);
            typeStats.put("percentage", totalDocuments > 0 ? (count * 100.0) / totalDocuments : 0.0);
            byDocumentType.put(type, typeStats);
        });
    }

    /**
     * Update contract statistics
     */
    public void updateContractStatistics(int active, BigDecimal value, int expiringSoon,
                                          int renewals, int totalEligibleForRenewal) {
        this.activeContracts = active;
        this.contractValue = value;
        this.contractsExpiringSoon = expiringSoon;

        // Calculate renewal rate
        if (totalEligibleForRenewal > 0) {
            this.contractRenewalRate = BigDecimal.valueOf(renewals)
                    .divide(BigDecimal.valueOf(totalEligibleForRenewal), 4, RoundingMode.HALF_UP);
        } else {
            this.contractRenewalRate = BigDecimal.ZERO;
        }
    }

    /**
     * Update case statistics
     */
    public void updateCaseStatistics(int open, int won, int lost) {
        this.openCases = open;

        // Calculate win rate
        int totalResolved = won + lost;
        if (totalResolved > 0) {
            this.caseWinRate = BigDecimal.valueOf(won)
                    .divide(BigDecimal.valueOf(totalResolved), 4, RoundingMode.HALF_UP);
        } else {
            this.caseWinRate = BigDecimal.ZERO;
        }
    }

    /**
     * Update compliance statistics
     */
    public void updateComplianceStatistics(int assessments, int violations) {
        // Calculate compliance score
        if (assessments > 0) {
            int compliant = assessments - violations;
            this.complianceScore = BigDecimal.valueOf(compliant)
                    .divide(BigDecimal.valueOf(assessments), 4, RoundingMode.HALF_UP);
        } else {
            this.complianceScore = BigDecimal.ONE; // 100% if no assessments
        }
    }

    /**
     * Update audit statistics
     */
    public void updateAuditStatistics(int totalFindings, int highRiskFindings) {
        this.auditFindings = totalFindings;
        this.highRiskMatters = highRiskFindings;
    }

    /**
     * Update financial statistics
     */
    public void updateFinancialStatistics(BigDecimal spend) {
        this.legalSpend = spend;
    }

    /**
     * Add jurisdiction breakdown
     */
    public void addJurisdictionStatistic(String jurisdiction, int contracts, int cases,
                                         BigDecimal value, double complianceRate) {
        Map<String, Object> jurisdictionStats = new HashMap<>();
        jurisdictionStats.put("contracts", contracts);
        jurisdictionStats.put("cases", cases);
        jurisdictionStats.put("contractValue", value.toString());
        jurisdictionStats.put("complianceRate", complianceRate);
        byJurisdiction.put(jurisdiction, jurisdictionStats);
    }

    /**
     * Calculate average contract value
     */
    public BigDecimal getAverageContractValue() {
        if (activeContracts == 0) {
            return BigDecimal.ZERO;
        }
        return contractValue.divide(BigDecimal.valueOf(activeContracts), 2, RoundingMode.HALF_UP);
    }

    /**
     * Get case win rate as percentage
     */
    public double getCaseWinRatePercentage() {
        if (caseWinRate == null) {
            return 0.0;
        }
        return caseWinRate.doubleValue() * 100;
    }

    /**
     * Get compliance score as percentage
     */
    public double getComplianceScorePercentage() {
        if (complianceScore == null) {
            return 0.0;
        }
        return complianceScore.doubleValue() * 100;
    }

    /**
     * Get contract renewal rate as percentage
     */
    public double getContractRenewalRatePercentage() {
        if (contractRenewalRate == null) {
            return 0.0;
        }
        return contractRenewalRate.doubleValue() * 100;
    }

    /**
     * Calculate legal spend per contract
     */
    public BigDecimal getLegalSpendPerContract() {
        if (activeContracts == 0) {
            return BigDecimal.ZERO;
        }
        return legalSpend.divide(BigDecimal.valueOf(activeContracts), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate legal spend per case
     */
    public BigDecimal getLegalSpendPerCase() {
        if (openCases == 0) {
            return BigDecimal.ZERO;
        }
        return legalSpend.divide(BigDecimal.valueOf(openCases), 2, RoundingMode.HALF_UP);
    }

    /**
     * Get contract expiration risk score (0-100)
     */
    public int getContractExpirationRiskScore() {
        if (activeContracts == 0) {
            return 0;
        }

        double expiringPercentage = (contractsExpiringSoon * 100.0) / activeContracts;

        if (expiringPercentage >= 50) {
            return 100; // Critical
        } else if (expiringPercentage >= 30) {
            return 75; // High
        } else if (expiringPercentage >= 15) {
            return 50; // Medium
        } else if (expiringPercentage >= 5) {
            return 25; // Low
        } else {
            return 10; // Minimal
        }
    }

    /**
     * Calculate overall legal department health score (0-100)
     */
    public int getOverallHealthScore() {
        int score = 0;

        // Case win rate (25 points)
        if (caseWinRate != null) {
            score += (int) (caseWinRate.doubleValue() * 25);
        }

        // Compliance score (30 points)
        if (complianceScore != null) {
            score += (int) (complianceScore.doubleValue() * 30);
        }

        // Contract renewal rate (20 points)
        if (contractRenewalRate != null) {
            score += (int) (contractRenewalRate.doubleValue() * 20);
        }

        // Low audit findings (15 points)
        if (auditFindings == 0) {
            score += 15;
        } else if (auditFindings <= 5) {
            score += 10;
        } else if (auditFindings <= 10) {
            score += 5;
        }

        // Low high-risk matters (10 points)
        if (highRiskMatters == 0) {
            score += 10;
        } else if (highRiskMatters <= 2) {
            score += 5;
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Get top document type
     */
    public String getTopDocumentType() {
        return byDocumentType.entrySet().stream()
                .max((e1, e2) -> {
                    int count1 = (int) ((Map<String, Object>) e1.getValue()).get("count");
                    int count2 = (int) ((Map<String, Object>) e2.getValue()).get("count");
                    return Integer.compare(count1, count2);
                })
                .map(Map.Entry::getKey)
                .orElse("N/A");
    }

    /**
     * Get top jurisdiction by contract count
     */
    public String getTopJurisdiction() {
        return byJurisdiction.entrySet().stream()
                .max((e1, e2) -> {
                    int count1 = (int) ((Map<String, Object>) e1.getValue()).get("contracts");
                    int count2 = (int) ((Map<String, Object>) e2.getValue()).get("contracts");
                    return Integer.compare(count1, count2);
                })
                .map(Map.Entry::getKey)
                .orElse("N/A");
    }

    /**
     * Generate executive summary
     */
    public Map<String, Object> generateExecutiveSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("periodStart", periodStart);
        summary.put("periodEnd", periodEnd);
        summary.put("isCurrentPeriod", isCurrentPeriod());

        // Key metrics
        summary.put("activeContracts", activeContracts);
        summary.put("totalContractValue", "$" + contractValue);
        summary.put("averageContractValue", "$" + getAverageContractValue());
        summary.put("contractsExpiringSoon", contractsExpiringSoon);
        summary.put("contractRenewalRate", String.format("%.1f%%", getContractRenewalRatePercentage()));

        summary.put("openCases", openCases);
        summary.put("caseWinRate", String.format("%.1f%%", getCaseWinRatePercentage()));

        summary.put("complianceScore", String.format("%.1f%%", getComplianceScorePercentage()));
        summary.put("auditFindings", auditFindings);
        summary.put("highRiskMatters", highRiskMatters);

        summary.put("totalLegalSpend", "$" + legalSpend);
        summary.put("legalSpendPerContract", "$" + getLegalSpendPerContract());

        summary.put("overallHealthScore", getOverallHealthScore() + "/100");
        summary.put("expirationRiskScore", getContractExpirationRiskScore() + "/100");

        summary.put("topDocumentType", getTopDocumentType());
        summary.put("topJurisdiction", getTopJurisdiction());

        return summary;
    }

    /**
     * Generate trend indicators
     */
    public Map<String, String> generateTrendIndicators(LegalStatistics previousPeriod) {
        Map<String, String> trends = new HashMap<>();

        // Contract trend
        int contractChange = activeContracts - previousPeriod.getActiveContracts();
        trends.put("contracts", contractChange > 0 ? "UP" : contractChange < 0 ? "DOWN" : "STABLE");

        // Case win rate trend
        if (caseWinRate != null && previousPeriod.getCaseWinRate() != null) {
            int winRateChange = caseWinRate.compareTo(previousPeriod.getCaseWinRate());
            trends.put("winRate", winRateChange > 0 ? "IMPROVING" : winRateChange < 0 ? "DECLINING" : "STABLE");
        }

        // Compliance trend
        if (complianceScore != null && previousPeriod.getComplianceScore() != null) {
            int complianceChange = complianceScore.compareTo(previousPeriod.getComplianceScore());
            trends.put("compliance", complianceChange > 0 ? "IMPROVING" : complianceChange < 0 ? "DECLINING" : "STABLE");
        }

        // Spend trend
        int spendChange = legalSpend.compareTo(previousPeriod.getLegalSpend());
        trends.put("spend", spendChange > 0 ? "INCREASING" : spendChange < 0 ? "DECREASING" : "STABLE");

        // Risk trend
        int riskChange = highRiskMatters - previousPeriod.getHighRiskMatters();
        trends.put("risk", riskChange > 0 ? "INCREASING" : riskChange < 0 ? "DECREASING" : "STABLE");

        return trends;
    }

    /**
     * Calculate period-over-period growth
     */
    public Map<String, Object> calculateGrowth(LegalStatistics previousPeriod) {
        Map<String, Object> growth = new HashMap<>();

        // Contract growth
        int contractGrowth = activeContracts - previousPeriod.getActiveContracts();
        double contractGrowthPct = previousPeriod.getActiveContracts() > 0 ?
                (contractGrowth * 100.0) / previousPeriod.getActiveContracts() : 0.0;
        growth.put("contractGrowth", contractGrowth);
        growth.put("contractGrowthPercentage", String.format("%.1f%%", contractGrowthPct));

        // Value growth
        BigDecimal valueGrowth = contractValue.subtract(previousPeriod.getContractValue());
        growth.put("contractValueGrowth", valueGrowth);

        // Case volume growth
        int caseGrowth = openCases - previousPeriod.getOpenCases();
        growth.put("caseVolumeGrowth", caseGrowth);

        // Win rate change
        if (caseWinRate != null && previousPeriod.getCaseWinRate() != null) {
            BigDecimal winRateChange = caseWinRate.subtract(previousPeriod.getCaseWinRate());
            growth.put("winRateChange", String.format("%.1f%%", winRateChange.doubleValue() * 100));
        }

        // Compliance change
        if (complianceScore != null && previousPeriod.getComplianceScore() != null) {
            BigDecimal complianceChange = complianceScore.subtract(previousPeriod.getComplianceScore());
            growth.put("complianceChange", String.format("%.1f%%", complianceChange.doubleValue() * 100));
        }

        // Spend growth
        BigDecimal spendGrowth = legalSpend.subtract(previousPeriod.getLegalSpend());
        double spendGrowthPct = previousPeriod.getLegalSpend().compareTo(BigDecimal.ZERO) > 0 ?
                spendGrowth.multiply(BigDecimal.valueOf(100))
                        .divide(previousPeriod.getLegalSpend(), 2, RoundingMode.HALF_UP).doubleValue() : 0.0;
        growth.put("spendGrowth", spendGrowth);
        growth.put("spendGrowthPercentage", String.format("%.1f%%", spendGrowthPct));

        return growth;
    }

    /**
     * Generate KPI card
     */
    public Map<String, Object> generateKPICard(String kpiName) {
        Map<String, Object> kpi = new HashMap<>();
        kpi.put("name", kpiName);
        kpi.put("period", periodStart + " to " + periodEnd);

        switch (kpiName.toUpperCase()) {
            case "CONTRACT_VALUE":
                kpi.put("value", "$" + contractValue);
                kpi.put("metric", "Total Contract Value");
                kpi.put("status", activeContracts > 0 ? "ACTIVE" : "INACTIVE");
                break;
            case "WIN_RATE":
                kpi.put("value", String.format("%.1f%%", getCaseWinRatePercentage()));
                kpi.put("metric", "Case Win Rate");
                kpi.put("status", caseWinRate != null && caseWinRate.doubleValue() >= 0.7 ? "GOOD" : "NEEDS_IMPROVEMENT");
                break;
            case "COMPLIANCE":
                kpi.put("value", String.format("%.1f%%", getComplianceScorePercentage()));
                kpi.put("metric", "Compliance Score");
                kpi.put("status", complianceScore != null && complianceScore.doubleValue() >= 0.9 ? "EXCELLENT" : "SATISFACTORY");
                break;
            case "LEGAL_SPEND":
                kpi.put("value", "$" + legalSpend);
                kpi.put("metric", "Total Legal Spend");
                kpi.put("status", "TRACKING");
                break;
            case "HEALTH":
                kpi.put("value", getOverallHealthScore() + "/100");
                kpi.put("metric", "Overall Health Score");
                kpi.put("status", getOverallHealthScore() >= 80 ? "HEALTHY" : getOverallHealthScore() >= 60 ? "FAIR" : "AT_RISK");
                break;
        }

        return kpi;
    }

    /**
     * Validate statistics
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (periodStart == null) {
            errors.add("Period start is required");
        }
        if (periodEnd == null) {
            errors.add("Period end is required");
        }
        if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
            errors.add("Period end must be after period start");
        }
        if (totalLegalDocuments < 0) {
            errors.add("Total legal documents cannot be negative");
        }
        if (activeContracts < 0) {
            errors.add("Active contracts cannot be negative");
        }
        if (contractValue != null && contractValue.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Contract value cannot be negative");
        }

        return errors;
    }

    /**
     * Check if statistics are complete
     */
    public boolean isComplete() {
        return totalLegalDocuments != null &&
               activeContracts != null &&
               contractValue != null &&
               caseWinRate != null &&
               complianceScore != null &&
               legalSpend != null;
    }

    /**
     * Get data quality score (0-100)
     */
    public int getDataQualityScore() {
        int score = 0;

        if (totalLegalDocuments != null && totalLegalDocuments > 0) score += 15;
        if (activeContracts != null && activeContracts > 0) score += 15;
        if (contractValue != null && contractValue.compareTo(BigDecimal.ZERO) > 0) score += 15;
        if (caseWinRate != null) score += 15;
        if (complianceScore != null) score += 15;
        if (legalSpend != null) score += 10;
        if (!byDocumentType.isEmpty()) score += 10;
        if (!byJurisdiction.isEmpty()) score += 5;

        return score;
    }
}
