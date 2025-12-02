package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "compliance_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ComplianceRule {

    @Id
    private UUID ruleId;

    @Column(unique = true, nullable = false)
    private String ruleCode;

    @Column(nullable = false)
    private String ruleName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleSeverity severity;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private Boolean isManual;

    @Column(nullable = false)
    private Boolean isRealTime;

    @Column(nullable = false)
    private Integer priority;

    // Rule conditions
    @ElementCollection
    @CollectionTable(name = "rule_conditions", joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "condition_name")
    @Column(name = "condition_value")
    private Map<String, String> conditions;

    // Thresholds
    @Column(precision = 19, scale = 2)
    private BigDecimal amountThreshold;

    private Integer countThreshold;

    private Integer timeWindowMinutes;

    private Integer frequencyThreshold;

    // Target scope
    @ElementCollection
    @CollectionTable(name = "rule_customer_types", joinColumns = @JoinColumn(name = "rule_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type")
    private List<CustomerType> applicableCustomerTypes;

    @ElementCollection
    @CollectionTable(name = "rule_transaction_types", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "transaction_type")
    private List<String> applicableTransactionTypes;

    @ElementCollection
    @CollectionTable(name = "rule_countries", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "country_code")
    private List<String> applicableCountries;

    // Actions
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleAction primaryAction;

    @ElementCollection
    @CollectionTable(name = "rule_secondary_actions", joinColumns = @JoinColumn(name = "rule_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private List<RuleAction> secondaryActions;

    @Column(columnDefinition = "TEXT")
    private String actionParameters;

    // Regulatory compliance
    @ElementCollection
    @CollectionTable(name = "rule_regulations", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "regulation_code")
    private List<String> regulatoryReferences;

    @ElementCollection
    @CollectionTable(name = "rule_jurisdictions", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "jurisdiction")
    private List<String> jurisdictions;

    // Performance metrics
    private Long executionCount;

    private Long violationCount;

    private Long falsePositiveCount;

    private Double accuracy;

    private Double effectiveness;

    private LocalDateTime lastExecuted;

    private LocalDateTime lastViolation;

    // Rule management
    @Column(nullable = false)
    private String createdBy;

    private String approvedBy;

    private LocalDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    private RuleStatus status;

    private LocalDateTime effectiveDate;

    private LocalDateTime expiryDate;

    @Column(columnDefinition = "TEXT")
    private String changeReason;

    @Column(columnDefinition = "TEXT")
    private String businessJustification;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String updatedBy;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (isManual == null) {
            isManual = false;
        }
        if (isRealTime == null) {
            isRealTime = true;
        }
        if (priority == null) {
            priority = 3; // Default medium priority
        }
        if (status == null) {
            status = RuleStatus.DRAFT;
        }
        if (executionCount == null) {
            executionCount = 0L;
        }
        if (violationCount == null) {
            violationCount = 0L;
        }
        if (falsePositiveCount == null) {
            falsePositiveCount = 0L;
        }
        if (accuracy == null) {
            accuracy = 0.0;
        }
        if (effectiveness == null) {
            effectiveness = 0.0;
        }
    }

    public enum RuleCategory {
        AML,
        KYC,
        SANCTIONS,
        TRANSACTION_MONITORING,
        CUSTOMER_SCREENING,
        REGULATORY_REPORTING,
        FRAUD_DETECTION,
        RISK_ASSESSMENT
    }

    public enum RuleType {
        THRESHOLD_BASED,
        PATTERN_BASED,
        VELOCITY_BASED,
        BEHAVIORAL_BASED,
        GEOGRAPHIC_BASED,
        ENTITY_BASED,
        TIME_BASED,
        STATISTICAL_BASED
    }

    public enum RuleSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum RuleAction {
        BLOCK_TRANSACTION,
        REQUIRE_APPROVAL,
        GENERATE_ALERT,
        ESCALATE_REVIEW,
        FILE_SAR,
        FILE_CTR,
        FREEZE_ACCOUNT,
        ENHANCED_MONITORING,
        NOTIFY_COMPLIANCE,
        LOG_INCIDENT,
        REQUIRE_DOCUMENTATION,
        SCHEDULE_REVIEW
    }

    public enum CustomerType {
        INDIVIDUAL,
        BUSINESS,
        GOVERNMENT,
        NON_PROFIT,
        FINANCIAL_INSTITUTION,
        MONEY_SERVICE_BUSINESS,
        TRUST,
        ESTATE
    }

    public enum RuleStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        ACTIVE,
        SUSPENDED,
        EXPIRED,
        RETIRED
    }
}