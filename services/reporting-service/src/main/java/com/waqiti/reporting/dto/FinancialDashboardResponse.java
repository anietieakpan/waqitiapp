package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDashboardResponse {
    
    private FinancialDashboardData dashboardData;
    private LocalDateTime lastUpdated;
    private Integer refreshIntervalMinutes;
    private Boolean successful;
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialDashboardData {
        private UUID userId;
        private DashboardType dashboardType;
        private LocalDateTime generatedAt;
        private ExecutiveMetrics executiveMetrics;
        private OperationalMetrics operationalMetrics;
        private RiskMetrics riskMetrics;
        private ComplianceMetrics complianceMetrics;
        private CustomerMetrics customerMetrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutiveMetrics {
        private BigDecimal totalAssets;
        private BigDecimal totalLiabilities;
        private BigDecimal totalEquity;
        private BigDecimal dailyTransactionVolume;
        private BigDecimal monthlyRevenue;
        private Long totalCustomers;
        private Long activeCustomers;
        private Double customerGrowthRate;
        private Double revenueGrowthRate;
        private Double returnOnAssets;
        private Double returnOnEquity;
        private BigDecimal netIncome;
        private Double profitMargin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationalMetrics {
        private Long dailyTransactionCount;
        private Double transactionSuccessRate;
        private Double averageTransactionProcessingTime;
        private Double systemUptime;
        private Double apiResponseTime;
        private Double errorRate;
        private String reconciliationStatus;
        private Double complianceScore;
        private Long pendingTransactions;
        private Long failedTransactions;
        private Double customerSatisfactionScore;
        private Long supportTicketsOpen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskMetrics {
        private Double overallRiskScore;
        private Double fraudDetectionRate;
        private Long suspiciousActivityCount;
        private Long complianceViolationCount;
        private Double liquidityRatio;
        private BigDecimal creditRiskExposure;
        private Long operationalRiskEvents;
        private Long regulatoryBreaches;
        private Double concentrationRisk;
        private BigDecimal valuAtRisk;
        private Double chargebackRate;
        private BigDecimal maxExposure;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceMetrics {
        private Double kycCompletionRate;
        private Long amlAlertsGenerated;
        private Long sarFilingCount;
        private Long ctrFilingCount;
        private Long ofacScreeningCount;
        private Long pepScreeningCount;
        private Double complianceTrainingCompletion;
        private Long auditFindingsCount;
        private Long overdueComplianceItems;
        private Double dataPrivacyScore;
        private Long gdprRequests;
        private Double regulatoryScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerMetrics {
        private Long newCustomerAcquisitions;
        private Double customerChurnRate;
        private BigDecimal averageCustomerLifetimeValue;
        private Double customerSatisfactionScore;
        private BigDecimal averageAccountBalance;
        private Double transactionFrequency;
        private Double productUtilizationRate;
        private Long customerSupportTickets;
        private Double customerEngagementScore;
        private BigDecimal averageTransactionValue;
        private Long activePaymentMethods;
        private Double retentionRate;
    }

    public enum DashboardType {
        EXECUTIVE_SUMMARY,
        OPERATIONAL_DASHBOARD,
        RISK_MANAGEMENT,
        COMPLIANCE_OVERVIEW,
        CUSTOMER_ANALYTICS
    }
}