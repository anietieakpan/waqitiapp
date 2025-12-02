package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud alert entity for tracking detected fraud cases
 * Supports multiple alert types, severity levels, and automated response actions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fraud_alerts", indexes = {
    @Index(name = "idx_fraud_alert_severity", columnList = "severity"),
    @Index(name = "idx_fraud_alert_status", columnList = "status"),
    @Index(name = "idx_fraud_alert_user", columnList = "user_id"),
    @Index(name = "idx_fraud_alert_transaction", columnList = "transaction_id"),
    @Index(name = "idx_fraud_alert_created", columnList = "created_at"),
    @Index(name = "idx_fraud_alert_type", columnList = "alert_type")
})
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    /**
     * Alert ID (alias for id for backward compatibility)
     */
    @Transient
    private String alertId;

    @Column(name = "alert_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    @Column(name = "severity", nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertLevel severity;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertStatus status;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "fraud_score", precision = 5, scale = 2)
    private BigDecimal fraudScore;

    @Column(name = "fraud_probability", precision = 5, scale = 4)
    private BigDecimal fraudProbability;

    @Column(name = "risk_level")
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "detection_method")
    private String detectionMethod;

    @Column(name = "rule_ids", columnDefinition = "TEXT")
    private String ruleIds; // JSON array of rule IDs

    @Column(name = "ml_model_version")
    private String mlModelVersion;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @ElementCollection
    @CollectionTable(name = "fraud_alert_indicators", 
        joinColumns = @JoinColumn(name = "alert_id"))
    @MapKeyColumn(name = "indicator_key")
    @Column(name = "indicator_value")
    private Map<String, String> indicators;

    @ElementCollection
    @CollectionTable(name = "fraud_alert_evidence", 
        joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "evidence")
    private List<String> evidence;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "geolocation")
    private String geolocation; // JSON object

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "source_system")
    private String sourceSystem;

    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Column(name = "acknowledged_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "resolved_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_action")
    @Enumerated(EnumType.STRING)
    private ResolutionAction resolutionAction;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "false_positive", nullable = false)
    @Builder.Default
    private Boolean falsePositive = false;

    @Column(name = "escalated", nullable = false)
    @Builder.Default
    private Boolean escalated = false;

    @Column(name = "escalated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime escalatedAt;

    @Column(name = "escalated_to")
    private String escalatedTo;

    @Column(name = "automated_action_taken")
    @Enumerated(EnumType.STRING)
    private AutomatedAction automatedActionTaken;

    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;

    @Column(name = "notification_channels", columnDefinition = "TEXT")
    private String notificationChannels; // JSON array

    @ElementCollection
    @CollectionTable(name = "fraud_alert_related_alerts", 
        joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "related_alert_id")
    private List<String> relatedAlerts;

    @ElementCollection
    @CollectionTable(name = "fraud_alert_tags", 
        joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "tag")
    private List<String> tags;

    @Column(name = "priority", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Column(name = "investigation_notes", columnDefinition = "TEXT")
    private String investigationNotes;

    @Column(name = "external_reference_id")
    private String externalReferenceId;

    @Column(name = "regulatory_filing_required", nullable = false)
    @Builder.Default
    private Boolean regulatoryFilingRequired = false;

    @Column(name = "filing_deadline")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime filingDeadline;

    @Column(name = "business_impact")
    @Enumerated(EnumType.STRING)
    private BusinessImpact businessImpact;

    @Column(name = "estimated_loss", precision = 19, scale = 2)
    private BigDecimal estimatedLoss;

    @Column(name = "actual_loss", precision = 19, scale = 2)
    private BigDecimal actualLoss;

    @Column(name = "recovery_amount", precision = 19, scale = 2)
    private BigDecimal recoveryAmount;

    @Version
    private Long version;

    /**
     * Alert types for different fraud scenarios
     */
    public enum AlertType {
        TRANSACTION_FRAUD,
        ACCOUNT_TAKEOVER,
        IDENTITY_THEFT,
        CARD_FRAUD,
        WIRE_FRAUD,
        CHECK_FRAUD,
        MONEY_LAUNDERING,
        SUSPICIOUS_ACTIVITY,
        VELOCITY_BREACH,
        PATTERN_ANOMALY,
        GEOLOCATION_ANOMALY,
        DEVICE_ANOMALY,
        BEHAVIORAL_ANOMALY,
        BLACKLIST_MATCH,
        WHITELIST_VIOLATION,
        THRESHOLD_BREACH,
        RULE_VIOLATION,
        ML_DETECTION,
        MANUAL_REVIEW,
        THIRD_PARTY_ALERT,
        REGULATORY_TRIGGER,
        COMPLIANCE_VIOLATION
    }

    /**
     * AlertLevel enum removed - uses canonical AlertLevel from same package
     * All references now use com.waqiti.common.fraud.model.AlertLevel (no import needed - same package)
     */

    /**
     * Alert status lifecycle
     */
    public enum AlertStatus {
        NEW,
        ACKNOWLEDGED,
        INVESTIGATING,
        PENDING_REVIEW,
        ESCALATED,
        RESOLVED,
        CLOSED,
        FALSE_POSITIVE,
        DUPLICATE
    }

    /**
     * Risk levels
     */
    public enum RiskLevel {
        VERY_HIGH,
        HIGH,
        MEDIUM,
        LOW,
        VERY_LOW
    }

    /**
     * Resolution actions
     */
    public enum ResolutionAction {
        NO_ACTION,
        ACCOUNT_LOCKED,
        CARD_BLOCKED,
        TRANSACTION_BLOCKED,
        USER_NOTIFIED,
        MANUAL_REVIEW_REQUIRED,
        ESCALATED_TO_COMPLIANCE,
        ESCALATED_TO_LAW_ENFORCEMENT,
        REGULATORY_FILING,
        SYSTEM_UPDATED,
        RULES_UPDATED,
        FALSE_POSITIVE_MARKED
    }

    /**
     * Automated actions taken
     */
    public enum AutomatedAction {
        NONE,
        TRANSACTION_BLOCKED,
        ACCOUNT_SUSPENDED,
        CARD_FROZEN,
        ADDITIONAL_VERIFICATION_REQUIRED,
        STEP_UP_AUTHENTICATION,
        VELOCITY_LIMIT_APPLIED,
        NOTIFICATION_SENT,
        COMPLIANCE_REPORT_GENERATED,
        CASE_CREATED,
        ESCALATION_TRIGGERED
    }

    /**
     * Priority levels
     */
    public enum Priority {
        URGENT,
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Business impact assessment
     */
    public enum BusinessImpact {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        MINIMAL
    }

    /**
     * Pre-persist callback
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = AlertStatus.NEW;
        }
        if (priority == null) {
            priority = Priority.MEDIUM;
        }
    }

    /**
     * Pre-update callback
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if alert is active (not resolved/closed)
     */
    public boolean isActive() {
        return status != AlertStatus.RESOLVED && 
               status != AlertStatus.CLOSED && 
               status != AlertStatus.FALSE_POSITIVE;
    }

    /**
     * Check if alert is high priority
     */
    public boolean isHighPriority() {
        return severity == AlertLevel.CRITICAL || 
               severity == AlertLevel.HIGH ||
               priority == Priority.URGENT ||
               priority == Priority.HIGH;
    }

    /**
     * Check if alert requires immediate action
     */
    public boolean requiresImmediateAction() {
        return severity == AlertLevel.CRITICAL ||
               (fraudScore != null && fraudScore.compareTo(new BigDecimal("90")) >= 0) ||
               priority == Priority.URGENT;
    }

    /**
     * Get age of alert in minutes
     */
    public long getAgeInMinutes() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes();
    }

    /**
     * Check if alert has exceeded SLA
     */
    public boolean hasSLAViolation() {
        long ageInMinutes = getAgeInMinutes();
        return switch (severity) {
            case CRITICAL -> ageInMinutes > 15; // 15 minutes for critical
            case HIGH -> ageInMinutes > 60;     // 1 hour for high
            case MEDIUM -> ageInMinutes > 240;   // 4 hours for medium
            case LOW -> ageInMinutes > 1440;     // 24 hours for low
            case INFO -> ageInMinutes > 4320;    // 3 days for info
        };
    }

    /**
     * Mark alert as acknowledged
     */
    public void acknowledge(String acknowledgedBy) {
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedAt = LocalDateTime.now();
        this.acknowledgedBy = acknowledgedBy;
    }

    /**
     * Escalate alert
     */
    public void escalate(String escalatedTo) {
        this.escalated = true;
        this.escalatedAt = LocalDateTime.now();
        this.escalatedTo = escalatedTo;
        this.status = AlertStatus.ESCALATED;
        this.priority = Priority.URGENT;
    }

    /**
     * Resolve alert
     */
    public void resolve(String resolvedBy, ResolutionAction action, String notes) {
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
        this.resolutionAction = action;
        this.resolutionNotes = notes;
    }

    /**
     * Mark as false positive
     */
    public void markAsFalsePositive(String reviewedBy, String reason) {
        this.status = AlertStatus.FALSE_POSITIVE;
        this.falsePositive = true;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = reviewedBy;
        this.resolutionNotes = reason;
    }

    /**
     * Get detected timestamp (alias for createdAt for compatibility)
     * @return The timestamp when this alert was detected/created
     */
    public LocalDateTime getDetectedAt() {
        return this.createdAt;
    }
}