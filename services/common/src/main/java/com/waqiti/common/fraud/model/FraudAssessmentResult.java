package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive fraud assessment result with detailed analysis and recommendations
 * Contains the complete fraud evaluation outcome with actionable intelligence
 */
@Data
@Builder
@Jacksonized
public class FraudAssessmentResult {
    
    private String assessmentId;
    private String requestId;
    private String transactionId;
    private String userId;
    private String accountId;
    
    // Overall assessment results
    private FraudDecision decision;
    private double overallRiskScore;
    private FraudScore overallFraudScore;  // Added for comprehensive scoring
    private String riskLevel;
    private double confidence;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant assessmentTimestamp;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant expirationTimestamp;
    
    // Detailed risk analysis breakdown
    private RiskBreakdown riskBreakdown;
    
    // Individual analysis results
    private IpFraudAnalysis ipAnalysis;
    private EmailFraudAnalysis emailAnalysis;
    private AccountFraudAnalysis accountAnalysis;
    private DeviceFraudAnalysis deviceAnalysis;
    private BehavioralFraudAnalysis behavioralAnalysis;
    private VelocityFraudAnalysis velocityAnalysis;
    private GeolocationFraudAnalysis geoAnalysis;
    private GeolocationFraudAnalysis geolocationAnalysis;
    private MLFraudAnalysis mlAnalysis;
    
    // Additional fields for compatibility
    private LocalDateTime assessedAt;
    private Long processingTimeMs;
    private boolean confident;
    private String errorMessage;
    
    // Compliance results
    private ComplianceResults complianceResults;
    
    // External data source results
    private Map<String, ExternalDataResult> externalDataResults;
    
    // Fraud indicators detected
    private List<FraudIndicator> fraudIndicators;
    private List<FraudIndicator> indicators;
    
    // Recommendations and actions
    private List<RecommendedAction> recommendedActions;
    private List<FraudMitigationAction> mitigationActions;
    private FraudMitigationAction recommendation;
    private boolean blocked;
    private boolean requiresManualReview;
    private List<SecurityFlag> securityFlags;
    private List<ComplianceFlag> complianceFlags;
    
    // Monitoring and alerting
    private List<Alert> alerts;
    private MonitoringRecommendations monitoring;
    
    // Audit trail
    private AuditTrail auditTrail;
    
    // Performance metrics
    private PerformanceMetrics performanceMetrics;
    
    /**
     * Fraud decision enumeration
     */
    public enum FraudDecision {
        APPROVE("Transaction approved"),
        DECLINE("Transaction declined due to fraud risk"),
        REVIEW("Transaction requires manual review"),
        CHALLENGE("Additional authentication required"),
        BLOCK_USER("User account blocked"),
        ESCALATE("Escalate to fraud investigation team");
        
        private final String description;
        
        FraudDecision(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Risk breakdown by category
     */
    @Data
    @Builder
    @Jacksonized
    public static class RiskBreakdown {
        private double ipRiskScore;
        private double deviceRiskScore;
        private double behavioralRiskScore;
        private double velocityRiskScore;
        private double geolocationRiskScore;
        private double accountRiskScore;
        private double transactionRiskScore;
        private double mlRiskScore;
        private double complianceRiskScore;
        private Map<String, Double> customRiskScores;
        
        public double getTotalRiskScore() {
            return ipRiskScore + deviceRiskScore + behavioralRiskScore + 
                   velocityRiskScore + geolocationRiskScore + accountRiskScore + 
                   transactionRiskScore + mlRiskScore + complianceRiskScore;
        }
    }
    
    /**
     * Compliance assessment results
     */
    @Data
    @Builder
    @Jacksonized
    public static class ComplianceResults {
        private boolean amlCheckPassed;
        private String amlCheckDetails;
        private boolean sanctionsCheckPassed;
        private String sanctionsCheckDetails;
        private boolean kycCheckPassed;
        private String kycCheckDetails;
        private boolean pciCompliancePass;
        private boolean gdprCompliancePass;
        private List<String> complianceViolations;
        private Map<String, String> regulatoryFlags;
    }
    
    /**
     * External data source result
     */
    @Data
    @Builder
    @Jacksonized
    public static class ExternalDataResult {
        private String sourceName;
        private boolean success;
        private double riskScore;
        private String status;
        private Map<String, Object> data;
        private String errorMessage;
        private long responseTimeMs;
    }
    
    /**
     * Recommended actions for risk mitigation
     */
    @Data
    @Builder
    @Jacksonized
    public static class RecommendedAction {
        private ActionType type;
        private String description;
        private Priority priority;
        private String reasoning;
        private Map<String, String> parameters;
        private boolean automated;
        
        public enum ActionType {
            BLOCK_TRANSACTION,
            REQUIRE_2FA,
            MANUAL_REVIEW,
            CONTACT_CUSTOMER,
            FREEZE_ACCOUNT,
            INCREASE_MONITORING,
            UPDATE_RISK_PROFILE,
            ESCALATE_TO_FRAUD_TEAM,
            NOTIFY_LAW_ENFORCEMENT,
            IMPLEMENT_VELOCITY_LIMITS
        }
        
        public enum Priority {
            LOW, MEDIUM, HIGH, CRITICAL, URGENT
        }
    }
    
    /**
     * Security flags raised during assessment
     */
    @Data
    @Builder
    @Jacksonized
    public static class SecurityFlag {
        private String type;
        private String severity;
        private String description;
        private String evidence;
        private Map<String, String> metadata;
        private boolean requiresAction;
    }
    
    /**
     * Compliance flags for regulatory requirements
     */
    @Data
    @Builder
    @Jacksonized
    public static class ComplianceFlag {
        private String regulation;
        private String violationType;
        private String description;
        private String remediation;
        private boolean mandatory;
        private String reportingRequired;
    }
    
    /**
     * Alert for immediate attention
     */
    @Data
    @Builder
    @Jacksonized
    public static class Alert {
        private String alertId;
        private AlertType type;
        private String severity;
        private String title;
        private String message;
        private Map<String, String> context;
        private boolean requiresImmediateAction;
        private String escalationPath;
        
        public enum AlertType {
            FRAUD_DETECTED,
            COMPLIANCE_VIOLATION,
            VELOCITY_EXCEEDED,
            SUSPICIOUS_BEHAVIOR,
            ACCOUNT_COMPROMISE,
            MONEY_LAUNDERING,
            SANCTIONS_HIT,
            SYSTEM_ALERT
        }
    }
    
    /**
     * Monitoring recommendations
     */
    @Data
    @Builder
    @Jacksonized
    public static class MonitoringRecommendations {
        private int monitoringDurationDays;
        private Set<String> metricsToMonitor;
        private Map<String, Double> thresholdAdjustments;
        private boolean enableRealTimeAlerts;
        private int alertFrequencyMinutes;
        private List<String> stakeholdersToNotify;
    }
    
    /**
     * Audit trail for compliance and forensics
     */
    @Data
    @Builder
    @Jacksonized
    public static class AuditTrail {
        private String assessmentEngine;
        private String engineVersion;
        private List<String> rulesApplied;
        private List<String> dataSourcesUsed;
        private Map<String, Object> inputParameters;
        private List<ProcessingStep> processingSteps;
        private String analystId;
        private boolean humanReviewRequired;
        
        @Data
        @Builder
        @Jacksonized
        public static class ProcessingStep {
            private String stepName;
            private Instant timestamp;
            private long durationMs;
            private String status;
            private Map<String, Object> results;
        }
    }
    
    /**
     * Performance metrics for system optimization
     */
    @Data
    @Builder
    @Jacksonized
    public static class PerformanceMetrics {
        private long totalProcessingTimeMs;
        private long mlModelInferenceTimeMs;
        private long externalApiCallsTimeMs;
        private long databaseQueryTimeMs;
        private long cacheHitRatio;
        private Map<String, Long> componentTimings;
        private int externalApiCalls;
        private int databaseQueries;
        private double cpuUsage;
        private double memoryUsage;
    }
    
    /**
     * Determines if the transaction should be approved based on the assessment
     */
    public boolean shouldApprove() {
        return decision == FraudDecision.APPROVE;
    }
    
    /**
     * Determines if the transaction requires manual review
     */
    public boolean requiresManualReview() {
        return decision == FraudDecision.REVIEW || 
               decision == FraudDecision.ESCALATE ||
               (alerts != null && alerts.stream().anyMatch(alert -> 
                   alert.isRequiresImmediateAction()));
    }
    
    /**
     * Gets the highest priority recommended action
     */
    public RecommendedAction getHighestPriorityAction() {
        if (recommendedActions == null || recommendedActions.isEmpty()) {
            return null;
        }
        
        return recommendedActions.stream()
            .max((a1, a2) -> a1.getPriority().ordinal() - a2.getPriority().ordinal())
            .orElse(null);
    }
    
    /**
     * Checks if any critical alerts were raised
     */
    public boolean hasCriticalAlerts() {
        return alerts != null && alerts.stream()
            .anyMatch(alert -> "CRITICAL".equals(alert.getSeverity()));
    }
    
    /**
     * Gets summary of all compliance violations
     */
    public List<String> getComplianceViolationSummary() {
        if (complianceResults == null) {
            return List.of();
        }
        return complianceResults.getComplianceViolations() != null ? 
               complianceResults.getComplianceViolations() : List.of();
    }
    
    /**
     * Calculates the weighted risk score considering confidence levels
     */
    public double getWeightedRiskScore() {
        return overallRiskScore * (confidence / 100.0);
    }
    
    /**
     * Check if transaction is blocked
     */
    public boolean isBlocked() {
        return decision == FraudDecision.DECLINE || decision == FraudDecision.BLOCK_USER;
    }
    
    /**
     * Check if manual review is required (alias for requiresManualReview)
     */
    public boolean isRequiresManualReview() {
        return requiresManualReview();
    }
}