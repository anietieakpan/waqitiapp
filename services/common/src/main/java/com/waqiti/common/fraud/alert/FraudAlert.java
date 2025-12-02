package com.waqiti.common.fraud.alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import javax.annotation.concurrent.NotThreadSafe;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.math.BigDecimal;
import com.waqiti.common.fraud.rules.FraudRuleViolation;
import com.waqiti.common.fraud.model.BehavioralAnomaly;
import com.waqiti.common.fraud.model.AlertLevel;

/**
 * Comprehensive fraud alert entity for the Waqiti P2P payment platform
 *
 * This class represents a fraud alert with complete risk assessment,
 * investigation tracking, and compliance integration capabilities.
 *
 * Features:
 * - Multi-level alert severity classification
 * - Complete audit trail with investigation workflow
 * - Integration with ML fraud scoring models
 * - Compliance reporting and regulatory submission
 * - Real-time alert escalation and notification
 * - Geographic and behavioral context tracking
 * - Financial impact assessment and risk quantification
 *
 * <h2>Thread Safety</h2>
 * <p><strong>NOT THREAD-SAFE</strong>. This class contains mutable state
 * and is not designed for concurrent modification. When passing to async methods,
 * create defensive copies using {@code toBuilder().build()}.
 * </p>
 *
 * @since 1.0.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@NotThreadSafe
public class FraudAlert {

    @NotNull
    @JsonProperty("alertId")
    private String alertId;

    @NotNull
    @JsonProperty("transactionId")
    private String transactionId;

    @NotNull
    @JsonProperty("userId")
    private String userId;

    @NotNull
    @JsonProperty("alertType")
    private AlertType alertType;

    @NotNull
    @JsonProperty("severity")
    private AlertSeverity severity;

    @NotNull
    @JsonProperty("level")
    private AlertLevel level;

    @NotNull
    @JsonProperty("status")
    private AlertStatus status;

    @JsonProperty("fraudScore")
    private com.waqiti.common.fraud.model.FraudScore fraudScore;

    @JsonProperty("violations")
    private List<FraudRuleViolation> violations;

    @JsonProperty("anomalies")
    private List<BehavioralAnomaly> anomalies;

    @NotNull
    @JsonProperty("riskScore")
    @Min(0)
    @Max(100)
    private Double riskScore;

    @JsonProperty("riskLevel")
    private com.waqiti.common.fraud.model.FraudRiskLevel riskLevel;

    @JsonProperty("escalated")
    @Builder.Default
    private Boolean escalated = false;

    @NotNull
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotNull
    @JsonProperty("currency")
    private String currency;

    @NotEmpty
    @JsonProperty("description")
    private String description;

    @JsonProperty("detailedReason")
    private String detailedReason;

    @NotNull
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime updatedAt;

    @JsonProperty("resolvedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime resolvedAt;

    @JsonProperty("investigatedBy")
    private String investigatedBy;

    @JsonProperty("investigationNotes")
    private String investigationNotes;

    @JsonProperty("resolutionAction")
    private ResolutionAction resolutionAction;

    @JsonProperty("escalationLevel")
    private EscalationLevel escalationLevel;

    @JsonProperty("escalationTimestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime escalationTimestamp;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("businessImpact")
    private BusinessImpact businessImpact;

    @JsonProperty("regulatoryReported")
    @Builder.Default
    private Boolean regulatoryReported = false;

    @JsonProperty("falsePositive")
    @Builder.Default
    private Boolean falsePositive = false;

    @JsonProperty("confirmedFraud")
    @Builder.Default
    private Boolean confirmedFraud = false;

    // Risk Assessment Details
    @JsonProperty("riskFactors")
    private List<RiskFactor> riskFactors;

    @JsonProperty("mlModelResults")
    private MLModelResults mlModelResults;

    @JsonProperty("behavioralIndicators")
    private BehavioralIndicators behavioralIndicators;

    @JsonProperty("geographicContext")
    private GeographicContext geographicContext;

    @JsonProperty("transactionContext")
    private TransactionContext transactionContext;

    // Investigation and Workflow
    @JsonProperty("investigationWorkflow")
    private InvestigationWorkflow investigationWorkflow;

    @JsonProperty("approvalChain")
    private List<ApprovalStep> approvalChain;

    @JsonProperty("auditTrail")
    private List<AuditEntry> auditTrail;

    // Compliance and Reporting
    @JsonProperty("complianceFlags")
    private ComplianceFlags complianceFlags;

    @JsonProperty("regulatorySubmissions")
    private List<RegulatorySubmission> regulatorySubmissions;

    @JsonProperty("sarRequired")
    @Builder.Default
    private Boolean sarRequired = false;

    @JsonProperty("ctrRequired")
    @Builder.Default
    private Boolean ctrRequired = false;

    // Notification and Communication
    @JsonProperty("notificationsSent")
    private List<NotificationRecord> notificationsSent;

    @JsonProperty("customerNotified")
    @Builder.Default
    private Boolean customerNotified = false;

    @JsonProperty("internalEscalated")
    @Builder.Default
    private Boolean internalEscalated = false;

    // Additional Context
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("relatedAlerts")
    private List<String> relatedAlerts;

    @JsonProperty("similarPatterns")
    private List<PatternReference> similarPatterns;

    @JsonProperty("historicalContext")
    private HistoricalContext historicalContext;

    // Performance Metrics
    @JsonProperty("detectionLatency")
    private Long detectionLatencyMs;

    @JsonProperty("investigationSLA")
    private InvestigationSLA investigationSLA;

    @JsonProperty("resolutionTime")
    private Long resolutionTimeMinutes;

    @JsonProperty("costImpact")
    private CostImpact costImpact;

    // Enums and Supporting Classes

    public enum AlertType {
        TRANSACTION_ANOMALY("Transaction Anomaly", "Unusual transaction pattern detected"),
        VELOCITY_BREACH("Velocity Breach", "Transaction velocity limits exceeded"),
        GEOGRAPHIC_ANOMALY("Geographic Anomaly", "Unusual location pattern"),
        BEHAVIORAL_CHANGE("Behavioral Change", "Significant behavior deviation"),
        AMOUNT_THRESHOLD("Amount Threshold", "High-value transaction alert"),
        TIME_PATTERN("Time Pattern", "Unusual timing pattern"),
        DEVICE_FINGERPRINT("Device Fingerprint", "Suspicious device characteristics"),
        NETWORK_ANALYSIS("Network Analysis", "Suspicious network connections"),
        ML_PREDICTION("ML Prediction", "Machine learning fraud prediction"),
        RULE_BASED("Rule Based", "Rule-based fraud detection"),
        COMPLIANCE_TRIGGER("Compliance Trigger", "Regulatory compliance alert"),
        MANUAL_REVIEW("Manual Review", "Manual investigation required"),
        ACCOUNT_TAKEOVER("Account Takeover", "Potential account compromise"),
        MONEY_LAUNDERING("Money Laundering", "AML pattern detection"),
        TERRORIST_FINANCING("Terrorist Financing", "CTF pattern detection");

        private final String displayName;
        private final String description;

        AlertType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum AlertSeverity {
        CRITICAL(1, "Critical", "Immediate action required"),
        HIGH(2, "High", "High priority investigation"),
        MEDIUM(3, "Medium", "Standard investigation"),
        LOW(4, "Low", "Low priority review"),
        INFORMATIONAL(5, "Informational", "For information only");

        private final int level;
        private final String displayName;
        private final String description;

        AlertSeverity(int level, String displayName, String description) {
            this.level = level;
            this.displayName = displayName;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum AlertStatus {
        NEW("New", "Alert created and awaiting triage"),
        TRIAGED("Triaged", "Alert has been triaged and assigned"),
        IN_PROGRESS("In Progress", "Investigation is in progress"),
        PENDING_APPROVAL("Pending Approval", "Awaiting management approval"),
        ESCALATED("Escalated", "Alert has been escalated"),
        RESOLVED("Resolved", "Alert has been resolved"),
        CLOSED("Closed", "Alert is closed"),
        REOPENED("Reopened", "Alert has been reopened"),
        FALSE_POSITIVE("False Positive", "Confirmed as false positive"),
        CONFIRMED_FRAUD("Confirmed Fraud", "Confirmed fraudulent activity");

        private final String displayName;
        private final String description;

        AlertStatus(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum ResolutionAction {
        BLOCK_TRANSACTION("Block Transaction", "Transaction was blocked"),
        BLOCK_USER("Block User", "User account was blocked"),
        REQUIRE_VERIFICATION("Require Verification", "Additional verification required"),
        MONITOR_ENHANCED("Monitor Enhanced", "Enhanced monitoring applied"),
        NO_ACTION("No Action", "No action taken"),
        MANUAL_REVIEW("Manual Review", "Sent for manual review"),
        ESCALATE_COMPLIANCE("Escalate Compliance", "Escalated to compliance team"),
        REPORT_AUTHORITIES("Report Authorities", "Reported to authorities"),
        EDUCATE_USER("Educate User", "User education provided"),
        ADJUST_LIMITS("Adjust Limits", "Transaction limits adjusted");

        private final String displayName;
        private final String description;

        ResolutionAction(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum EscalationLevel {
        NONE(0, "None", "No escalation"),
        SUPERVISOR(1, "Supervisor", "Escalated to supervisor"),
        MANAGER(2, "Manager", "Escalated to manager"),
        COMPLIANCE(3, "Compliance", "Escalated to compliance"),
        EXECUTIVE(4, "Executive", "Escalated to executive"),
        REGULATORY(5, "Regulatory", "Escalated to regulatory");

        private final int level;
        private final String displayName;
        private final String description;

        EscalationLevel(int level, String displayName, String description) {
            this.level = level;
            this.displayName = displayName;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum BusinessImpact {
        NEGLIGIBLE("Negligible", "Minimal business impact"),
        LOW("Low", "Low business impact"),
        MEDIUM("Medium", "Medium business impact"),
        HIGH("High", "High business impact"),
        CRITICAL("Critical", "Critical business impact");

        private final String displayName;
        private final String description;

        BusinessImpact(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // Supporting Classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorType;
        private String factorName;
        private Double weight;
        private Double contribution;
        private String description;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MLModelResults {
        private String modelName;
        private String modelVersion;
        private Double fraudProbability;
        private Double confidenceScore;
        private Map<String, Double> featureImportance;
        private List<String> activatedRules;
        private LocalDateTime evaluatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralIndicators {
        private Double deviationScore;
        private List<String> anomalousPatterns;
        private Map<String, Double> behaviorMetrics;
        private String baselinePeriod;
        private LocalDateTime lastNormalBehavior;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicContext {
        private String currentLocation;
        private String previousLocation;
        private Double distanceKm;
        private Double velocityKmh;
        private Boolean impossibleTravel;
        private String riskCountry;
        private String ipAddress;
        private String vpnDetected;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionContext {
        private String merchantCategory;
        private String paymentMethod;
        private String channel;
        private Map<String, Object> transactionAttributes;
        private List<String> relatedTransactions;
        private String timeOfDay;
        private String dayOfWeek;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestigationWorkflow {
        private String workflowId;
        private String currentStage;
        private List<WorkflowStep> steps;
        private Map<String, Object> workflowData;
        private LocalDateTime startedAt;
        private LocalDateTime expectedCompletion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStep {
        private String stepId;
        private String stepName;
        private String status;
        private String assignedTo;
        private LocalDateTime completedAt;
        private String outcome;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalStep {
        private String approver;
        private String approvalLevel;
        private String decision;
        private String comments;
        private LocalDateTime approvedAt;
        private Boolean required;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEntry {
        private String action;
        private String performedBy;
        private LocalDateTime timestamp;
        private String details;
        private Map<String, Object> beforeState;
        private Map<String, Object> afterState;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceFlags {
        private Boolean amlTriggered;
        private Boolean ctfTriggered;
        private Boolean kycRequired;
        private Boolean enhancedDueDiligence;
        private List<String> regulatoryRequirements;
        private Map<String, String> complianceNotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatorySubmission {
        private String submissionId;
        private String regulatoryBody;
        private String reportType;
        private LocalDateTime submittedAt;
        private String status;
        private String confirmationNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationRecord {
        private String notificationType;
        private String channel;
        private String recipient;
        private LocalDateTime sentAt;
        private String status;
        private String messageId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternReference {
        private String patternId;
        private String patternType;
        private Double similarity;
        private String description;
        private LocalDateTime lastSeen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalContext {
        private Integer previousAlerts;
        private Integer falsePositiveRate;
        private LocalDateTime lastAlert;
        private String userRiskProfile;
        private Map<String, Object> historicalPatterns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestigationSLA {
        private LocalDateTime targetResolution;
        private Boolean slaBreached;
        private Long remainingTimeMinutes;
        private String slaCategory;
        private String escalationTrigger;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostImpact {
        private BigDecimal investigationCost;
        private BigDecimal potentialLoss;
        private BigDecimal preventedLoss;
        private BigDecimal operationalCost;
        private String currency;
    }

    // Utility Methods
    public boolean isHighRisk() {
        return riskScore != null && riskScore >= 70.0;
    }

    public boolean isCritical() {
        return AlertSeverity.CRITICAL.equals(severity);
    }

    public boolean requiresRegulatory() {
        return sarRequired || ctrRequired;
    }

    public boolean isOverdue() {
        return investigationSLA != null && 
               investigationSLA.getSlaBreached() != null && 
               investigationSLA.getSlaBreached();
    }

    public boolean isResolved() {
        return AlertStatus.RESOLVED.equals(status) || 
               AlertStatus.CLOSED.equals(status) ||
               AlertStatus.FALSE_POSITIVE.equals(status) ||
               AlertStatus.CONFIRMED_FRAUD.equals(status);
    }

    public String getDisplaySummary() {
        return String.format("Alert %s: %s - %s (Risk: %.1f%%)", 
                           alertId, 
                           alertType.getDisplayName(), 
                           severity.getDisplayName(), 
                           riskScore != null ? riskScore : 0.0);
    }

    public LocalDateTime getEffectiveUpdatedAt() {
        return updatedAt != null ? updatedAt : createdAt;
    }

    public boolean hasMLPrediction() {
        return mlModelResults != null && mlModelResults.getFraudProbability() != null;
    }

    public boolean isGeographicAnomaly() {
        return geographicContext != null && 
               geographicContext.getImpossibleTravel() != null && 
               geographicContext.getImpossibleTravel();
    }

    public Double getFinancialImpact() {
        return amount != null ? amount.doubleValue() : 0.0;
    }

    public String getRiskCategory() {
        if (riskScore == null) return "Unknown";
        if (riskScore >= 90) return "Extreme";
        if (riskScore >= 70) return "High";
        if (riskScore >= 50) return "Medium";
        if (riskScore >= 30) return "Low";
        return "Minimal";
    }

    /**
     * Get alert level (maps severity to level)
     * Fixed: Now uses canonical AlertLevel from model package
     */
    public AlertLevel getLevel() {
        if (severity == null) {
            return AlertLevel.LOW;
        }
        return switch (severity) {
            case CRITICAL -> AlertLevel.CRITICAL;
            case HIGH -> AlertLevel.HIGH;
            case MEDIUM -> AlertLevel.MEDIUM;
            case LOW -> AlertLevel.LOW;
            case INFORMATIONAL -> AlertLevel.INFO;
        };
    }

    /**
     * PRODUCTION FIX: Get fraud score object
     * Returns existing fraudScore if available, or builds one from riskScore
     */
    public com.waqiti.common.fraud.model.FraudScore getFraudScore() {
        // Return existing fraudScore if available
        if (fraudScore != null) {
            return fraudScore;
        }

        // Build from riskScore
        return com.waqiti.common.fraud.model.FraudScore.builder()
            .score(riskScore != null ? riskScore : 0.0)
            .riskLevel(com.waqiti.common.fraud.model.FraudRiskLevel.fromScore(riskScore != null ? riskScore : 0.0))
            .confidence(0.8)
            .calculatedAt(createdAt)
            .build();
    }

    /**
     * PRODUCTION FIX: Get risk level enum
     */
    public com.waqiti.common.fraud.model.FraudRiskLevel getRiskLevel() {
        return com.waqiti.common.fraud.model.FraudRiskLevel.fromScore(riskScore != null ? riskScore : 0.0);
    }

    /**
     * PRODUCTION FIX: Get timestamp (alias for createdAt)
     */
    public LocalDateTime getTimestamp() {
        return createdAt;
    }

    /**
     * PRODUCTION FIX: toBuilder() is now generated by Lombok with @Builder(toBuilder = true)
     * Removed manual implementation - Lombok generates it automatically
     */
}