package com.waqiti.frauddetection.entity;

import com.waqiti.frauddetection.dto.RiskLevel;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fraud Incident Entity - Real-time fraud detection incidents
 * 
 * Production-grade incident tracking with:
 * - Real-time alerting
 * - Automated response workflows
 * - Integration with SIEM systems
 * - Complete audit trail
 * 
 * @author Waqiti Fraud Detection Team
 */
@Entity
@Table(name = "fraud_incidents", indexes = {
    @Index(name = "idx_fraud_incident_transaction", columnList = "transaction_id"),
    @Index(name = "idx_fraud_incident_user", columnList = "user_id"),
    @Index(name = "idx_fraud_incident_severity", columnList = "severity"),
    @Index(name = "idx_fraud_incident_status", columnList = "status"),
    @Index(name = "idx_fraud_incident_detected", columnList = "detected_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudIncident {
    
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @NotNull
    @Column(name = "incident_number", unique = true, nullable = false, length = 50)
    private String incidentNumber;
    
    // Transaction Context
    @NotNull
    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;
    
    @Column(name = "user_id", length = 36)
    private String userId;
    
    @Column(name = "account_id", length = 36)
    private String accountId;
    
    @Column(name = "transaction_amount", precision = 19, scale = 4)
    private BigDecimal transactionAmount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    // Incident Classification
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 50)
    private IncidentType incidentType;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private IncidentSeverity severity;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;
    
    // Detection Information
    @Column(name = "fraud_score", precision = 5, scale = 4)
    private BigDecimal fraudScore;
    
    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_incident_detection_rules",
        joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "rule_id")
    @Builder.Default
    private List<String> triggeredRules = new ArrayList<>();
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_incident_indicators",
        joinColumns = @JoinColumn(name = "incident_id"))
    @MapKeyColumn(name = "indicator_type")
    @Column(name = "indicator_value")
    @Builder.Default
    private Map<String, String> fraudIndicators = new HashMap<>();
    
    @Column(name = "detection_method", length = 50)
    private String detectionMethod; // ML_MODEL, RULE_ENGINE, MANUAL, EXTERNAL_ALERT
    
    @Column(name = "detected_by_model", length = 100)
    private String detectedByModel;
    
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;
    
    // Status and Workflow
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private IncidentStatus status = IncidentStatus.NEW;
    
    // Automated Response
    @Column(name = "auto_blocked", nullable = false)
    @Builder.Default
    private Boolean autoBlocked = false;
    
    @Column(name = "auto_blocked_at")
    private LocalDateTime autoBlockedAt;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_incident_auto_actions",
        joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "action")
    @Builder.Default
    private List<String> automatedActions = new ArrayList<>();
    
    // Investigation
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;
    
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
    
    @Column(name = "investigated", nullable = false)
    @Builder.Default
    private Boolean investigated = false;
    
    @Column(name = "investigation_notes", columnDefinition = "TEXT")
    private String investigationNotes;
    
    // Resolution
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 30)
    private IncidentResolution resolution;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;
    
    @Column(name = "false_positive", nullable = false)
    @Builder.Default
    private Boolean falsePositive = null;
    
    // Alert and Notification
    @Column(name = "alert_sent", nullable = false)
    @Builder.Default
    private Boolean alertSent = false;
    
    @Column(name = "alert_sent_at")
    private LocalDateTime alertSentAt;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_incident_notifications",
        joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "notification_channel")
    @Builder.Default
    private List<String> notificationChannels = new ArrayList<>();
    
    // Related Incidents
    @Column(name = "case_id", length = 36)
    private String caseId; // Link to FraudCase if escalated
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_incident_related",
        joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "related_incident_id")
    @Builder.Default
    private List<String> relatedIncidents = new ArrayList<>();
    
    @Column(name = "pattern_id", length = 36)
    private String patternId;
    
    // SLA Tracking
    @Column(name = "sla_response_time_minutes")
    private Integer slaResponseTimeMinutes;
    
    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;
    
    @Column(name = "sla_breached", nullable = false)
    @Builder.Default
    private Boolean slaBreached = false;
    
    // Metadata
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_incident_metadata",
        joinColumns = @JoinColumn(name = "incident_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    // Audit Fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if incident is active
     */
    public boolean isActive() {
        return status == IncidentStatus.NEW || 
               status == IncidentStatus.INVESTIGATING ||
               status == IncidentStatus.PENDING_ACTION;
    }
    
    /**
     * Check if requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return severity == IncidentSeverity.CRITICAL && isActive();
    }
    
    /**
     * Calculate response time
     */
    public Long getResponseTimeMinutes() {
        if (detectedAt == null) return null;
        LocalDateTime responseTime = assignedAt != null ? assignedAt : LocalDateTime.now();
        return java.time.Duration.between(detectedAt, responseTime).toMinutes();
    }
    
    /**
     * Incident Type Enum
     */
    public enum IncidentType {
        SUSPICIOUS_TRANSACTION,
        ACCOUNT_TAKEOVER,
        CARD_FRAUD,
        IDENTITY_THEFT,
        VELOCITY_VIOLATION,
        GEOLOCATION_ANOMALY,
        DEVICE_FRAUD,
        MERCHANT_FRAUD,
        FRIENDLY_FRAUD,
        MONEY_LAUNDERING,
        PHISHING_ATTEMPT,
        BRUTE_FORCE_ATTACK,
        CREDENTIAL_STUFFING,
        BOT_ACTIVITY,
        SYNTHETIC_IDENTITY,
        OTHER
    }
    
    /**
     * Incident Severity Enum
     */
    public enum IncidentSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Incident Status Enum
     */
    public enum IncidentStatus {
        NEW,
        ACKNOWLEDGED,
        INVESTIGATING,
        PENDING_ACTION,
        RESOLVED,
        CLOSED,
        ESCALATED,
        FALSE_POSITIVE
    }
    
    /**
     * Incident Resolution Enum
     */
    public enum IncidentResolution {
        CONFIRMED_FRAUD,
        FALSE_POSITIVE,
        LEGITIMATE_ACTIVITY,
        NO_ACTION_NEEDED,
        ESCALATED_TO_CASE,
        CUSTOMER_VERIFIED,
        TIMEOUT_CLOSED
    }
}
