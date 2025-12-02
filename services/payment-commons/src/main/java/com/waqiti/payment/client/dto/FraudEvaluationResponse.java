package com.waqiti.payment.client.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive fraud evaluation response
 * Industrial-grade fraud detection result with ML insights
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FraudEvaluationResponse {
    
    private UUID evaluationId;
    private UUID paymentId;
    private EvaluationStatus status;
    private LocalDateTime evaluatedAt;
    private Long processingTimeMs;
    
    // Core fraud assessment
    @Builder.Default
    private BigDecimal overallRiskScore = BigDecimal.ZERO; // 0-100
    
    private RiskLevel riskLevel;
    private FraudDecision decision;
    private Double confidence; // 0.0-1.0
    
    // Detailed risk analysis
    private RiskBreakdown riskBreakdown;
    
    // ML model results
    @Builder.Default
    private List<MLModelResult> mlModelResults = List.of();
    
    // Rule engine results
    @Builder.Default
    private List<RuleResult> ruleResults = List.of();
    
    // Recommendations
    private FraudRecommendation recommendation;
    
    // Detailed analysis
    private BehavioralAnalysis behavioralAnalysis;
    private GeographicAnalysis geographicAnalysis;
    private VelocityAnalysis velocityAnalysis;
    private DeviceAnalysis deviceAnalysis;
    
    // Compliance checks
    private ComplianceResults complianceResults;
    
    // Additional context
    private Map<String, Object> additionalData;
    private String reason;
    private String detailedExplanation;
    
    // Monitoring and alerting
    @Builder.Default
    private List<FraudAlert> alertsGenerated = List.of();
    
    private MonitoringRecommendation monitoringRecommendation;
    
    public enum EvaluationStatus {
        SUCCESS,
        PARTIAL,
        FAILED,
        TIMEOUT,
        ERROR
    }
    
    public enum RiskLevel {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH,
        CRITICAL
    }
    
    public enum FraudDecision {
        APPROVE,
        APPROVE_WITH_MONITORING,
        REQUIRE_ADDITIONAL_VERIFICATION,
        MANUAL_REVIEW,
        DECLINE,
        BLOCK
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskBreakdown {
        private BigDecimal behavioralRisk;
        private BigDecimal geographicRisk;
        private BigDecimal velocityRisk;
        private BigDecimal deviceRisk;
        private BigDecimal networkRisk;
        private BigDecimal accountRisk;
        private BigDecimal transactionRisk;
        private BigDecimal merchantRisk;
        private Map<String, BigDecimal> customRiskFactors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MLModelResult {
        private String modelName;
        private String modelVersion;
        private BigDecimal score;
        private Double confidence;
        private Map<String, Object> features;
        private String explanation;
        private LocalDateTime modelLastTrained;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleResult {
        private String ruleId;
        private String ruleName;
        private RuleStatus status;
        private BigDecimal score;
        private String condition;
        private Map<String, Object> matchedValues;
        private String explanation;
        
        public enum RuleStatus {
            MATCHED,
            NOT_MATCHED,
            ERROR,
            SKIPPED
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudRecommendation {
        private RecommendedAction primaryAction;
        @Builder.Default
        private List<RecommendedAction> alternativeActions = List.of();
        private String reasoning;
        private Integer urgencyLevel; // 1-10
        private LocalDateTime reviewBy;
        private Map<String, String> actionParameters;
        
        public enum RecommendedAction {
            PROCEED,
            MONITOR,
            VERIFY_IDENTITY,
            VERIFY_PAYMENT_METHOD,
            REQUIRE_3DS,
            MANUAL_REVIEW,
            DECLINE_TRANSACTION,
            BLOCK_USER,
            ESCALATE_TO_SPECIALIST
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralAnalysis {
        private BigDecimal behavioralScore;
        private boolean isUnusualPattern;
        private boolean isUnusualTiming;
        private boolean isUnusualAmount;
        private boolean isUnusualRecipient;
        private Integer transactionFrequency;
        private BigDecimal averageTransactionAmount;
        private String typicalTransactionPattern;
        private Map<String, Object> deviations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicAnalysis {
        private BigDecimal geographicRisk;
        private String userLocation;
        private String transactionLocation;
        private Double distanceFromUsual;
        private boolean isHighRiskCountry;
        private boolean isUnusualLocation;
        private boolean isVpnDetected;
        private boolean isTorDetected;
        private String ipReputationScore;
        private Map<String, Object> locationHistory;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityAnalysis {
        private BigDecimal velocityRisk;
        private Integer transactionsLast24h;
        private BigDecimal volumeLast24h;
        private Integer transactionsLastWeek;
        private BigDecimal volumeLastWeek;
        private Integer transactionsLastMonth;
        private BigDecimal volumeLastMonth;
        @Builder.Default
        private List<VelocityLimitViolation> violations = List.of();
        private boolean isUnusualVelocity;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VelocityLimitViolation {
            private String limitType;
            private BigDecimal limit;
            private BigDecimal actual;
            private String timeframe;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceAnalysis {
        private BigDecimal deviceRisk;
        private boolean isKnownDevice;
        private boolean isCompromisedDevice;
        private boolean isSuspiciousDevice;
        private String deviceReputation;
        private Integer deviceAge;
        private String deviceFingerprint;
        private Map<String, Object> deviceAttributes;
        private List<String> deviceFlags;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceResults {
        private boolean amlPassed;
        private boolean kycPassed;
        private boolean sanctionsCheckPassed;
        private boolean pepCheckPassed;
        private boolean ofacCheckPassed;
        @Builder.Default
        private List<ComplianceViolation> violations = List.of();
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComplianceViolation {
            private String type;
            private String description;
            private String severity;
            private Map<String, Object> details;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudAlert {
        private String alertId;
        private AlertType type;
        private AlertSeverity severity;
        private String message;
        private LocalDateTime triggeredAt;
        private Map<String, Object> alertData;
        
        public enum AlertType {
            HIGH_RISK_TRANSACTION,
            VELOCITY_VIOLATION,
            GEOGRAPHIC_ANOMALY,
            DEVICE_ANOMALY,
            BEHAVIORAL_ANOMALY,
            COMPLIANCE_VIOLATION,
            BLACKLIST_MATCH,
            PATTERN_MATCH
        }
        
        public enum AlertSeverity {
            LOW,
            MEDIUM,
            HIGH,
            CRITICAL
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonitoringRecommendation {
        private MonitoringLevel level;
        private Integer durationDays;
        @Builder.Default
        private List<String> monitoringActions = List.of();
        private Map<String, Object> monitoringParameters;
        
        public enum MonitoringLevel {
            NONE,
            BASIC,
            ENHANCED,
            INTENSIVE,
            CONTINUOUS
        }
    }
    
    // Business logic methods
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || 
               riskLevel == RiskLevel.VERY_HIGH || 
               riskLevel == RiskLevel.CRITICAL;
    }
    
    public boolean shouldBlock() {
        return decision == FraudDecision.DECLINE || 
               decision == FraudDecision.BLOCK;
    }
    
    public boolean requiresManualReview() {
        return decision == FraudDecision.MANUAL_REVIEW ||
               decision == FraudDecision.REQUIRE_ADDITIONAL_VERIFICATION;
    }
    
    public boolean hasComplianceIssues() {
        return complianceResults != null && 
               (!complianceResults.isAmlPassed() || 
                !complianceResults.isKycPassed() || 
                !complianceResults.isSanctionsCheckPassed() ||
                !complianceResults.isPepCheckPassed() ||
                !complianceResults.isOfacCheckPassed());
    }
    
    public boolean hasVelocityViolations() {
        return velocityAnalysis != null && 
               velocityAnalysis.getViolations() != null && 
               !velocityAnalysis.getViolations().isEmpty();
    }
    
    public Integer getHighSeverityAlertCount() {
        return (int) alertsGenerated.stream()
            .filter(alert -> alert.getSeverity() == FraudAlert.AlertSeverity.HIGH || 
                           alert.getSeverity() == FraudAlert.AlertSeverity.CRITICAL)
            .count();
    }
}