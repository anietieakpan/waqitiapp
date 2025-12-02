package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Event model for fraud detection activities and audit trail
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionEvent {
    
    private String eventId;
    private String eventType;
    private String transactionId;
    private String userId;
    private String accountId;
    private String sessionId;
    
    // Fraud details
    private String fraudReason;
    private String fraudType;
    private double riskScore;
    private double confidence;
    private String severity;
    
    // Detection details
    private String detectionMethod;
    private String modelVersion;
    private String ruleId;
    private String detectedBy;
    
    // Transaction context
    private String routingNumber;
    private String amount;
    private String currency;
    private String merchantId;
    private String paymentMethod;
    
    // Location and device
    private String ipAddress;
    private String deviceId;
    private String location;
    private String userAgent;
    
    // Action taken
    private boolean blocked;
    private boolean requiresManualReview;
    private FraudMitigationAction recommendation;
    private String actionTaken;
    
    // Indicators and evidence
    private List<FraudIndicator> fraudIndicators;
    private Map<String, Object> evidenceData;
    private List<String> triggeredRules;
    
    // Timing
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime detectedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime resolvedAt;
    
    // Investigation details
    private String investigationStatus;
    private String investigatedBy;
    private String investigationNotes;
    private String resolution;
    private boolean falsePositive;
    
    // Compliance and reporting
    private boolean reportableEvent;
    private List<String> complianceFlags;
    private boolean lawEnforcementNotified;
    private String caseNumber;
    
    // System metadata
    private String sourceSystem;
    private String correlationId;
    private String parentEventId;
    private Map<String, String> metadata;
    
    /**
     * Event types for fraud detection
     */
    public static class EventType {
        public static final String ROUTING_NUMBER_FRAUD = "ROUTING_NUMBER_FRAUD";
        public static final String IMAGE_ALTERATION_DETECTED = "IMAGE_ALTERATION_DETECTED";
        public static final String VELOCITY_VIOLATION = "VELOCITY_VIOLATION";
        public static final String BEHAVIORAL_ANOMALY = "BEHAVIORAL_ANOMALY";
        public static final String ML_FRAUD_DETECTED = "ML_FRAUD_DETECTED";
        public static final String COMPREHENSIVE_FRAUD_ASSESSMENT = "COMPREHENSIVE_FRAUD_ASSESSMENT";
        public static final String ROUTING_FRAUD_CHECK_ERROR = "ROUTING_FRAUD_CHECK_ERROR";
        public static final String IMAGE_ANALYSIS_ERROR = "IMAGE_ANALYSIS_ERROR";
        public static final String BLACKLIST_HIT = "BLACKLIST_HIT";
        public static final String SUSPICIOUS_PATTERN = "SUSPICIOUS_PATTERN";
        public static final String GEOLOCATION_ANOMALY = "GEOLOCATION_ANOMALY";
        public static final String DEVICE_ANOMALY = "DEVICE_ANOMALY";
        public static final String ACCOUNT_TAKEOVER = "ACCOUNT_TAKEOVER";
        public static final String SYNTHETIC_IDENTITY = "SYNTHETIC_IDENTITY";
    }
    
    /**
     * Fraud severity levels
     */
    public static class Severity {
        public static final String LOW = "LOW";
        public static final String MEDIUM = "MEDIUM";
        public static final String HIGH = "HIGH";
        public static final String CRITICAL = "CRITICAL";
    }
    
    /**
     * Investigation status values
     */
    public static class InvestigationStatus {
        public static final String PENDING = "PENDING";
        public static final String IN_PROGRESS = "IN_PROGRESS";
        public static final String RESOLVED = "RESOLVED";
        public static final String CLOSED = "CLOSED";
        public static final String ESCALATED = "ESCALATED";
    }
    
    /**
     * Check if event requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return Severity.CRITICAL.equals(severity) ||
               blocked ||
               (riskScore >= 0.9) ||
               lawEnforcementNotified;
    }
    
    /**
     * Check if event is high priority
     */
    public boolean isHighPriority() {
        return Severity.HIGH.equals(severity) ||
               Severity.CRITICAL.equals(severity) ||
               requiresManualReview ||
               (riskScore >= 0.7);
    }
    
    /**
     * Check if investigation is complete
     */
    public boolean isInvestigationComplete() {
        return InvestigationStatus.RESOLVED.equals(investigationStatus) ||
               InvestigationStatus.CLOSED.equals(investigationStatus);
    }
    
    /**
     * Get event priority level
     */
    public String getPriorityLevel() {
        if (requiresImmediateAttention()) {
            return "URGENT";
        } else if (isHighPriority()) {
            return "HIGH";
        } else if (Severity.MEDIUM.equals(severity)) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    /**
     * Add metadata entry
     */
    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * Add fraud indicator
     */
    public void addFraudIndicator(FraudIndicator indicator) {
        if (fraudIndicators == null) {
            fraudIndicators = new java.util.ArrayList<>();
        }
        fraudIndicators.add(indicator);
    }
    
    /**
     * Add triggered rule
     */
    public void addTriggeredRule(String ruleId) {
        if (triggeredRules == null) {
            triggeredRules = new java.util.ArrayList<>();
        }
        triggeredRules.add(ruleId);
    }
    
    /**
     * Mark as resolved
     */
    public void markAsResolved(String resolution, String resolvedBy) {
        this.resolution = resolution;
        this.investigatedBy = resolvedBy;
        this.investigationStatus = InvestigationStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.processedAt = LocalDateTime.now();
    }
    
    /**
     * Mark as false positive
     */
    public void markAsFalsePositive(String reason, String reviewedBy) {
        this.falsePositive = true;
        this.resolution = "False Positive: " + reason;
        this.investigatedBy = reviewedBy;
        this.investigationStatus = InvestigationStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }
}