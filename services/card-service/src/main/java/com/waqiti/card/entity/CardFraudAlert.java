package com.waqiti.card.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardFraudAlert entity - Fraud alert record
 * Represents a fraud alert triggered by fraud detection rules
 *
 * Alerts are generated when transactions match fraud rules
 * and require investigation or action
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_fraud_alert", indexes = {
    @Index(name = "idx_fraud_alert_id", columnList = "alert_id"),
    @Index(name = "idx_fraud_alert_transaction", columnList = "transaction_id"),
    @Index(name = "idx_fraud_alert_card", columnList = "card_id"),
    @Index(name = "idx_fraud_alert_status", columnList = "alert_status"),
    @Index(name = "idx_fraud_alert_severity", columnList = "severity"),
    @Index(name = "idx_fraud_alert_date", columnList = "alert_date"),
    @Index(name = "idx_fraud_alert_rule", columnList = "triggered_rule_id"),
    @Index(name = "idx_fraud_alert_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardFraudAlert extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // ALERT IDENTIFICATION
    // ========================================================================

    @Column(name = "alert_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Alert ID is required")
    private String alertId;

    @Column(name = "alert_type", length = 50)
    @Size(max = 50)
    private String alertType;

    @Column(name = "alert_category", length = 50)
    @Size(max = 50)
    private String alertCategory;

    // ========================================================================
    // REFERENCES
    // ========================================================================

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "authorization_id")
    private UUID authorizationId;

    @Column(name = "card_id", nullable = false)
    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    @Column(name = "triggered_rule_id")
    private UUID triggeredRuleId;

    // ========================================================================
    // ALERT DETAILS
    // ========================================================================

    @Column(name = "alert_status", nullable = false, length = 30)
    @NotBlank(message = "Alert status is required")
    @Builder.Default
    private String alertStatus = "OPEN";

    @Column(name = "severity", nullable = false, length = 20)
    @NotBlank(message = "Severity is required")
    private String severity;

    @Column(name = "alert_date", nullable = false)
    @NotNull
    @Builder.Default
    private LocalDateTime alertDate = LocalDateTime.now();

    @Column(name = "alert_message", columnDefinition = "TEXT")
    private String alertMessage;

    @Column(name = "alert_description", columnDefinition = "TEXT")
    private String alertDescription;

    // ========================================================================
    // RISK ASSESSMENT
    // ========================================================================

    @Column(name = "risk_score", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal riskScore;

    @Column(name = "risk_level", length = 20)
    @Size(max = 20)
    private String riskLevel;

    @Column(name = "fraud_probability", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal fraudProbability;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal confidenceScore;

    // ========================================================================
    // TRANSACTION CONTEXT
    // ========================================================================

    @Column(name = "transaction_amount", precision = 18, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "currency_code", length = 3)
    @Size(min = 3, max = 3)
    private String currencyCode;

    @Column(name = "merchant_id", length = 100)
    @Size(max = 100)
    private String merchantId;

    @Column(name = "merchant_name", length = 255)
    @Size(max = 255)
    private String merchantName;

    @Column(name = "merchant_category_code", length = 4)
    @Size(min = 4, max = 4)
    private String merchantCategoryCode;

    @Column(name = "merchant_country", length = 3)
    @Size(min = 2, max = 3)
    private String merchantCountry;

    @Column(name = "is_international")
    private Boolean isInternational;

    @Column(name = "is_online")
    private Boolean isOnline;

    // ========================================================================
    // FRAUD INDICATORS
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fraud_indicators", columnDefinition = "jsonb")
    private Map<String, Object> fraudIndicators;

    @Column(name = "velocity_breach")
    private Boolean velocityBreach;

    @Column(name = "amount_threshold_breach")
    private Boolean amountThresholdBreach;

    @Column(name = "geographic_anomaly")
    private Boolean geographicAnomaly;

    @Column(name = "behavioral_anomaly")
    private Boolean behavioralAnomaly;

    @Column(name = "device_fingerprint_mismatch")
    private Boolean deviceFingerprintMismatch;

    @Column(name = "suspicious_merchant")
    private Boolean suspiciousMerchant;

    // ========================================================================
    // ACTIONS TAKEN
    // ========================================================================

    @Column(name = "transaction_blocked")
    @Builder.Default
    private Boolean transactionBlocked = false;

    @Column(name = "card_blocked")
    @Builder.Default
    private Boolean cardBlocked = false;

    @Column(name = "user_notified")
    @Builder.Default
    private Boolean userNotified = false;

    @Column(name = "fraud_team_notified")
    @Builder.Default
    private Boolean fraudTeamNotified = false;

    @Column(name = "manual_review_required")
    @Builder.Default
    private Boolean manualReviewRequired = false;

    @Column(name = "action_taken", length = 50)
    @Size(max = 50)
    private String actionTaken;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions_log", columnDefinition = "jsonb")
    private java.util.List<Map<String, Object>> actionsLog;

    // ========================================================================
    // INVESTIGATION
    // ========================================================================

    @Column(name = "investigation_status", length = 30)
    @Size(max = 30)
    private String investigationStatus;

    @Column(name = "assigned_to", length = 100)
    @Size(max = 100)
    private String assignedTo;

    @Column(name = "investigation_started_date")
    private LocalDateTime investigationStartedDate;

    @Column(name = "investigation_completed_date")
    private LocalDateTime investigationCompletedDate;

    @Column(name = "investigation_notes", columnDefinition = "TEXT")
    private String investigationNotes;

    // ========================================================================
    // RESOLUTION
    // ========================================================================

    @Column(name = "resolution_status", length = 30)
    @Size(max = 30)
    private String resolutionStatus;

    @Column(name = "resolution_date")
    private LocalDateTime resolutionDate;

    @Column(name = "resolution_outcome", length = 50)
    @Size(max = 50)
    private String resolutionOutcome;

    @Column(name = "is_true_positive")
    private Boolean isTruePositive;

    @Column(name = "is_false_positive")
    private Boolean isFalsePositive;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 100)
    @Size(max = 100)
    private String resolvedBy;

    // ========================================================================
    // ESCALATION
    // ========================================================================

    @Column(name = "escalated")
    @Builder.Default
    private Boolean escalated = false;

    @Column(name = "escalation_date")
    private LocalDateTime escalationDate;

    @Column(name = "escalation_level")
    @Min(1)
    @Max(5)
    private Integer escalationLevel;

    @Column(name = "escalated_to", length = 100)
    @Size(max = 100)
    private String escalatedTo;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    // ========================================================================
    // LAW ENFORCEMENT
    // ========================================================================

    @Column(name = "reported_to_law_enforcement")
    @Builder.Default
    private Boolean reportedToLawEnforcement = false;

    @Column(name = "law_enforcement_report_date")
    private LocalDateTime lawEnforcementReportDate;

    @Column(name = "law_enforcement_case_number", length = 100)
    @Size(max = 100)
    private String lawEnforcementCaseNumber;

    // ========================================================================
    // MACHINE LEARNING
    // ========================================================================

    @Column(name = "ml_model_version", length = 50)
    @Size(max = 50)
    private String mlModelVersion;

    @Column(name = "ml_features", columnDefinition = "TEXT")
    private String mlFeatures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ml_prediction_details", columnDefinition = "jsonb")
    private Map<String, Object> mlPredictionDetails;

    // ========================================================================
    // METADATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if alert is open/active
     */
    @Transient
    public boolean isActive() {
        return "OPEN".equals(alertStatus) ||
               "INVESTIGATING".equals(alertStatus) ||
               "UNDER_REVIEW".equals(alertStatus);
    }

    /**
     * Check if alert is resolved
     */
    @Transient
    public boolean isResolved() {
        return "RESOLVED".equals(alertStatus) ||
               "CLOSED".equals(alertStatus) ||
               "FALSE_POSITIVE".equals(alertStatus) ||
               "CONFIRMED_FRAUD".equals(alertStatus);
    }

    /**
     * Check if high severity
     */
    @Transient
    public boolean isHighSeverity() {
        return "CRITICAL".equals(severity) || "HIGH".equals(severity);
    }

    /**
     * Check if requires immediate action
     */
    @Transient
    public boolean requiresImmediateAction() {
        return isHighSeverity() &&
               (manualReviewRequired || transactionBlocked || cardBlocked);
    }

    /**
     * Mark as under investigation
     */
    public void startInvestigation(String assignee) {
        this.alertStatus = "INVESTIGATING";
        this.investigationStatus = "IN_PROGRESS";
        this.assignedTo = assignee;
        this.investigationStartedDate = LocalDateTime.now();
    }

    /**
     * Resolve as true positive (confirmed fraud)
     */
    public void resolveAsTruePositive(String notes, String resolvedBy) {
        this.alertStatus = "CONFIRMED_FRAUD";
        this.resolutionStatus = "CONFIRMED";
        this.resolutionOutcome = "TRUE_POSITIVE";
        this.isTruePositive = true;
        this.isFalsePositive = false;
        this.resolutionDate = LocalDateTime.now();
        this.resolutionNotes = notes;
        this.resolvedBy = resolvedBy;
        this.investigationCompletedDate = LocalDateTime.now();

        // Update the triggered rule statistics
        if (triggeredRuleId != null) {
            // This will be done in the service layer
        }
    }

    /**
     * Resolve as false positive (legitimate transaction)
     */
    public void resolveAsFalsePositive(String notes, String resolvedBy) {
        this.alertStatus = "FALSE_POSITIVE";
        this.resolutionStatus = "FALSE_ALARM";
        this.resolutionOutcome = "FALSE_POSITIVE";
        this.isTruePositive = false;
        this.isFalsePositive = true;
        this.resolutionDate = LocalDateTime.now();
        this.resolutionNotes = notes;
        this.resolvedBy = resolvedBy;
        this.investigationCompletedDate = LocalDateTime.now();

        // Update the triggered rule statistics
        if (triggeredRuleId != null) {
            // This will be done in the service layer
        }
    }

    /**
     * Escalate alert
     */
    public void escalate(int level, String escalatedTo, String reason) {
        this.escalated = true;
        this.escalationDate = LocalDateTime.now();
        this.escalationLevel = level;
        this.escalatedTo = escalatedTo;
        this.escalationReason = reason;
        this.alertStatus = "ESCALATED";
    }

    /**
     * Report to law enforcement
     */
    public void reportToLawEnforcement(String caseNumber) {
        this.reportedToLawEnforcement = true;
        this.lawEnforcementReportDate = LocalDateTime.now();
        this.lawEnforcementCaseNumber = caseNumber;
    }

    /**
     * Block transaction
     */
    public void blockTransaction() {
        this.transactionBlocked = true;
        this.actionTaken = "TRANSACTION_BLOCKED";
        addActionLog("BLOCK_TRANSACTION", "Transaction blocked due to fraud alert");
    }

    /**
     * Block card
     */
    public void blockCard() {
        this.cardBlocked = true;
        this.actionTaken = "CARD_BLOCKED";
        addActionLog("BLOCK_CARD", "Card blocked due to fraud alert");
    }

    /**
     * Notify user
     */
    public void notifyUser() {
        this.userNotified = true;
        addActionLog("NOTIFY_USER", "User notified of suspicious activity");
    }

    /**
     * Notify fraud team
     */
    public void notifyFraudTeam() {
        this.fraudTeamNotified = true;
        addActionLog("NOTIFY_FRAUD_TEAM", "Fraud team notified");
    }

    /**
     * Add action log entry
     */
    private void addActionLog(String action, String description) {
        if (actionsLog == null) {
            actionsLog = new java.util.ArrayList<>();
        }
        Map<String, Object> logEntry = Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "action", action,
            "description", description
        );
        actionsLog.add(logEntry);
    }

    /**
     * Close alert
     */
    public void close(String notes) {
        this.alertStatus = "CLOSED";
        this.resolutionDate = LocalDateTime.now();
        this.resolutionNotes = notes;
        this.investigationCompletedDate = LocalDateTime.now();
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (alertStatus == null) {
            alertStatus = "OPEN";
        }
        if (alertDate == null) {
            alertDate = LocalDateTime.now();
        }
        if (transactionBlocked == null) {
            transactionBlocked = false;
        }
        if (cardBlocked == null) {
            cardBlocked = false;
        }
        if (userNotified == null) {
            userNotified = false;
        }
        if (fraudTeamNotified == null) {
            fraudTeamNotified = false;
        }
        if (manualReviewRequired == null) {
            manualReviewRequired = false;
        }
        if (escalated == null) {
            escalated = false;
        }
        if (reportedToLawEnforcement == null) {
            reportedToLawEnforcement = false;
        }
    }
}
