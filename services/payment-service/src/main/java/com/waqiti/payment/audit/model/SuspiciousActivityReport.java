package com.waqiti.payment.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Suspicious Activity Report (SAR)
 * 
 * Comprehensive report for suspicious activity detection including:
 * - Pattern analysis and anomaly detection
 * - User behavior profiling
 * - Transaction velocity analysis
 * - Geographic anomalies
 * - Regulatory reporting requirements
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousActivityReport {
    
    // Report identification
    private String reportId;
    private LocalDateTime generatedAt;
    private LocalDateTime reportPeriodStart;
    private LocalDateTime reportPeriodEnd;
    private ReportType reportType;
    private ReportStatus reportStatus;
    
    // Summary statistics
    private int totalSuspiciousEvents;
    private int uniqueUsersInvolved;
    private int uniqueIpAddresses;
    private BigDecimal totalSuspiciousAmount;
    private String primaryCurrency;
    
    // Risk assessment
    private OverallRiskLevel overallRiskLevel;
    private Map<String, Integer> riskScoreDistribution;
    private List<RiskTrend> riskTrends;
    
    // Suspicious patterns
    private List<SuspiciousPattern> detectedPatterns;
    private Map<String, Integer> patternFrequency;
    private List<EmergingThreat> emergingThreats;
    
    // User analysis
    private List<SuspiciousUser> suspiciousUsers;
    private Map<UUID, Integer> userRiskScores;
    private List<UserBehaviorAnomaly> behaviorAnomalies;
    
    // Transaction analysis
    private List<SuspiciousTransaction> suspiciousTransactions;
    private Map<String, BigDecimal> unusualAmountsByType;
    private List<VelocityViolation> velocityViolations;
    
    // Geographic analysis
    private List<GeographicAnomaly> geographicAnomalies;
    private Map<String, Integer> suspiciousCountriesCounts;
    private List<CrossBorderAlert> crossBorderAlerts;
    
    // Network analysis
    private List<NetworkCluster> suspiciousNetworks;
    private Map<String, List<String>> linkedAccounts;
    private List<MoneyLaunderingIndicator> amlIndicators;
    
    // Regulatory compliance
    private boolean filingRequired;
    private String regulatoryAuthority;
    private LocalDateTime filingDeadline;
    private String filingStatus;
    private List<ComplianceViolation> complianceViolations;
    
    // Investigation recommendations
    private List<InvestigationRecommendation> recommendations;
    private List<String> immediateActions;
    private Map<String, String> evidenceLocations;
    
    // Enums
    public enum ReportType {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        ON_DEMAND,
        INCIDENT_BASED,
        REGULATORY_REQUIRED
    }
    
    public enum ReportStatus {
        DRAFT,
        PENDING_REVIEW,
        APPROVED,
        FILED,
        REJECTED,
        ARCHIVED
    }
    
    public enum OverallRiskLevel {
        MINIMAL,
        LOW,
        MODERATE,
        ELEVATED,
        HIGH,
        CRITICAL
    }
    
    // Supporting classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousPattern {
        private String patternId;
        private String patternType;
        private String description;
        private int occurrences;
        private List<String> affectedUserIds;
        private LocalDateTime firstDetected;
        private LocalDateTime lastDetected;
        private String severity;
        private Map<String, Object> patternMetadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousUser {
        private UUID userId;
        private String userName;
        private String userEmail;
        private int suspiciousEventCount;
        private BigDecimal totalSuspiciousAmount;
        private List<String> suspiciousPatterns;
        private Integer riskScore;
        private String recommendedAction;
        private boolean accountFrozen;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousTransaction {
        private String transactionId;
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime transactionTime;
        private String suspicionReason;
        private List<String> redFlags;
        private Integer riskScore;
        private String transactionType;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserBehaviorAnomaly {
        private UUID userId;
        private String anomalyType;
        private String normalBehavior;
        private String observedBehavior;
        private double deviationScore;
        private LocalDateTime detectedAt;
        private String severity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityViolation {
        private UUID userId;
        private String violationType;
        private int transactionCount;
        private BigDecimal totalAmount;
        private String timeWindow;
        private int limitExceeded;
        private LocalDateTime violationTime;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicAnomaly {
        private UUID userId;
        private String anomalyType;
        private String expectedLocation;
        private String actualLocation;
        private double distanceKm;
        private double timeHours;
        private boolean impossibleTravel;
        private LocalDateTime detectedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrossBorderAlert {
        private String transactionId;
        private String originCountry;
        private String destinationCountry;
        private BigDecimal amount;
        private String currency;
        private boolean highRiskCountry;
        private boolean sanctionedCountry;
        private List<String> complianceFlags;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkCluster {
        private String clusterId;
        private List<UUID> connectedUsers;
        private int clusterSize;
        private BigDecimal totalFlowAmount;
        private String suspectedActivity;
        private double confidenceScore;
        private Map<String, Object> networkMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoneyLaunderingIndicator {
        private String indicatorType;
        private String description;
        private List<String> involvedAccounts;
        private BigDecimal suspiciousAmount;
        private String layeringPattern;
        private double mlRiskScore;
        private boolean reportable;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceViolation {
        private String violationId;
        private String regulationCode;
        private String violationDescription;
        private LocalDateTime occurredAt;
        private String severity;
        private BigDecimal potentialFine;
        private String remediationRequired;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestigationRecommendation {
        private String recommendationId;
        private String priority;
        private String recommendedAction;
        private String justification;
        private List<String> targetUsers;
        private String assignedTo;
        private LocalDateTime dueDate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskTrend {
        private String trendType;
        private String direction;
        private double changePercentage;
        private String timeframe;
        private String significance;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergingThreat {
        private String threatId;
        private String threatType;
        private String description;
        private int affectedUsers;
        private LocalDateTime firstObserved;
        private String estimatedImpact;
        private String mitigationStrategy;
    }
}