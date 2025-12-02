package com.waqiti.frauddetection.entity;

import com.waqiti.frauddetection.alert.FraudAlertService;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fraud Alert Entity
 * 
 * Stores fraud alerts for high-risk transactions requiring manual review
 * 
 * @author Waqiti Security Team
 * @version 1.0
 */
@Entity
@Table(name = "fraud_alerts", indexes = {
    @Index(name = "idx_alert_id", columnList = "alert_id"),
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_severity", columnList = "severity"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", unique = true, nullable = false, length = 50)
    private String alertId;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "severity", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FraudAlertService.AlertSeverity severity;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private FraudAlertService.AlertStatus status;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private Double riskScore;

    @Column(name = "ml_score", precision = 5, scale = 2)
    private Double mlScore;

    @Column(name = "rule_score", precision = 5, scale = 2)
    private Double ruleScore;

    @Column(name = "triggered_rules_count")
    private Integer triggeredRulesCount;

    @Column(name = "triggered_rules", columnDefinition = "TEXT")
    private String triggeredRules;

    @Column(name = "is_blocked", nullable = false)
    private boolean isBlocked;

    @Column(name = "requires_manual_review", nullable = false)
    private boolean requiresManualReview;

    @Column(name = "confirmed_fraud")
    private Boolean confirmedFraud;

    @ElementCollection
    @CollectionTable(name = "fraud_alert_metadata", 
        joinColumns = @JoinColumn(name = "alert_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value", columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = FraudAlertService.AlertStatus.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}