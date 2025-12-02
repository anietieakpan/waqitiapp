package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fraud Rule Definition Entity
 * 
 * Stores configurable fraud detection rules in database
 * Allows dynamic rule updates without code redeployment
 * 
 * @author Waqiti Security Team
 * @version 1.0
 */
@Entity
@Table(name = "fraud_rule_definitions", indexes = {
    @Index(name = "idx_rule_code", columnList = "rule_code"),
    @Index(name = "idx_enabled", columnList = "enabled"),
    @Index(name = "idx_priority", columnList = "priority")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRuleDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", unique = true, nullable = false, length = 100)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "severity", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RuleSeverity severity;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private Double riskScore;

    @Column(name = "weight", precision = 5, scale = 2)
    private Double weight;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @ElementCollection
    @CollectionTable(name = "fraud_rule_transaction_types", 
        joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "transaction_type")
    private List<String> transactionTypes;

    @ElementCollection
    @CollectionTable(name = "fraud_rule_parameters", 
        joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value", columnDefinition = "TEXT")
    private Map<String, Object> parameters;

    @Column(name = "historical_accuracy", precision = 5, scale = 2)
    private Double historicalAccuracy;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (version == null) {
            version = 1;
        }
        if (priority == null) {
            priority = 50;
        }
        if (weight == null) {
            weight = 1.0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RuleSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}