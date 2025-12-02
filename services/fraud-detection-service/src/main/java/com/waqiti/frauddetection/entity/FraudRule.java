package com.waqiti.frauddetection.entity;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fraud Detection Rule Entity
 * 
 * Production-grade configurable fraud rules with:
 * - Rule versioning and history
 * - A/B testing support
 * - Performance tracking
 * - Dynamic rule evaluation
 * 
 * @author Waqiti Fraud Detection Team
 */
@Entity
@Table(name = "fraud_rules", indexes = {
    @Index(name = "idx_fraud_rule_type", columnList = "rule_type"),
    @Index(name = "idx_fraud_rule_active", columnList = "active"),
    @Index(name = "idx_fraud_rule_priority", columnList = "priority")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRule {
    
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @NotNull
    @Column(name = "rule_code", unique = true, nullable = false, length = 50)
    private String ruleCode;
    
    @NotNull
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private RuleType ruleType;
    
    @NotNull
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 50; // 1=highest, 100=lowest
    
    // Rule Logic
    @Column(name = "rule_expression", columnDefinition = "TEXT", nullable = false)
    private String ruleExpression; // SpEL or custom DSL
    
    @Column(name = "threshold_value", precision = 19, scale = 4)
    private java.math.BigDecimal thresholdValue;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_rule_conditions",
        joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "condition_key")
    @Column(name = "condition_value")
    @Builder.Default
    private Map<String, String> conditions = new java.util.HashMap<>();
    
    // Actions
    @Enumerated(EnumType.STRING)
    @Column(name = "action_on_match", length = 30)
    @Builder.Default
    private RuleAction actionOnMatch = RuleAction.FLAG_FOR_REVIEW;
    
    @Column(name = "risk_score_impact", precision = 5, scale = 4)
    private java.math.BigDecimal riskScoreImpact; // How much to add/subtract from risk score
    
    // Status
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;
    
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;
    
    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;
    
    // Performance Tracking
    @Column(name = "total_evaluations")
    @Builder.Default
    private Long totalEvaluations = 0L;
    
    @Column(name = "total_matches")
    @Builder.Default
    private Long totalMatches = 0L;
    
    @Column(name = "true_positives")
    @Builder.Default
    private Long truePositives = 0L;
    
    @Column(name = "false_positives")
    @Builder.Default
    private Long falsePositives = 0L;
    
    @Column(name = "precision_score", precision = 5, scale = 4)
    private java.math.BigDecimal precisionScore;
    
    @Column(name = "last_matched_at")
    private LocalDateTime lastMatchedAt;
    
    // Testing and Validation
    @Column(name = "in_testing", nullable = false)
    @Builder.Default
    private Boolean inTesting = false;
    
    @Column(name = "ab_test_group", length = 20)
    private String abTestGroup; // A, B, CONTROL
    
    @Column(name = "ab_test_percentage")
    private Integer abTestPercentage; // 0-100
    
    // Metadata
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fraud_rule_metadata",
        joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    @Builder.Default
    private Map<String, String> metadata = new java.util.HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
    
    /**
     * Check if rule should be evaluated
     */
    public boolean shouldEvaluate() {
        LocalDateTime now = LocalDateTime.now();
        return active && 
               (effectiveFrom == null || !now.isBefore(effectiveFrom)) &&
               (effectiveUntil == null || !now.isAfter(effectiveUntil));
    }
    
    /**
     * Calculate match rate
     */
    public Double getMatchRate() {
        return totalEvaluations > 0 
            ? (double) totalMatches / totalEvaluations 
            : 0.0;
    }
    
    /**
     * Rule Type Enum
     */
    public enum RuleType {
        VELOCITY_CHECK,
        AMOUNT_THRESHOLD,
        GEOLOCATION,
        DEVICE_FINGERPRINT,
        BEHAVIORAL_PATTERN,
        MERCHANT_RISK,
        TIME_OF_DAY,
        TRANSACTION_PATTERN,
        BLACKLIST_CHECK,
        WHITELIST_CHECK,
        ML_SCORE_THRESHOLD,
        CUSTOM
    }
    
    /**
     * Rule Action Enum
     */
    public enum RuleAction {
        BLOCK_TRANSACTION,
        FLAG_FOR_REVIEW,
        REQUIRE_STEP_UP_AUTH,
        INCREASE_MONITORING,
        TRIGGER_ALERT,
        NO_ACTION
    }
}
