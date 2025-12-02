package com.waqiti.reconciliation.domain;

import com.waqiti.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_rules", indexes = {
    @Index(name = "idx_rule_name", columnList = "ruleName"),
    @Index(name = "idx_rule_type", columnList = "ruleType"),
    @Index(name = "idx_is_active", columnList = "isActive"),
    @Index(name = "idx_priority", columnList = "priority"),
    @Index(name = "idx_account_pattern", columnList = "accountPattern")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReconciliationRule extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;
    
    @Column(name = "account_pattern", length = 100)
    private String accountPattern;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "priority", nullable = false)
    private Integer priority = 100;
    
    @Column(name = "auto_match", nullable = false)
    private Boolean autoMatch = false;
    
    @Column(name = "tolerance_amount", precision = 19, scale = 4)
    private BigDecimal toleranceAmount;
    
    @Column(name = "tolerance_percentage", precision = 5, scale = 4)
    private BigDecimal tolerancePercentage;
    
    @Column(name = "max_date_variance_days")
    private Integer maxDateVarianceDays;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "matching_strategy", nullable = false)
    private MatchingStrategy matchingStrategy;
    
    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "reconciliation_rule_fields", 
                     joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "matching_field")
    private List<MatchingField> matchingFields;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_rule_conditions", 
                     joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "condition_key")
    @Column(name = "condition_value", columnDefinition = "TEXT")
    private Map<String, String> conditions;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_rule_actions", 
                     joinColumns = @JoinColumn(name = "rule_id"))
    @MapKeyColumn(name = "action_key")
    @Column(name = "action_value", columnDefinition = "TEXT")
    private Map<String, String> actions;
    
    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;
    
    @Column(name = "notification_on_match", nullable = false)
    private Boolean notificationOnMatch = false;
    
    @Column(name = "notification_on_exception", nullable = false)
    private Boolean notificationOnException = true;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;
    
    @Column(name = "execution_count")
    private Long executionCount = 0L;
    
    @Column(name = "successful_matches")
    private Long successfulMatches = 0L;
    
    @Column(name = "failed_matches")
    private Long failedMatches = 0L;
    
    @Column(name = "last_executed_at")
    private java.time.LocalDateTime lastExecutedAt;
    
    public enum RuleType {
        EXACT_MATCH,
        FUZZY_MATCH,
        TOLERANCE_MATCH,
        AGGREGATE_MATCH,
        PATTERN_MATCH,
        SPLIT_MATCH,
        COMBINED_MATCH,
        EXCEPTION_RULE,
        AUTO_ADJUSTMENT,
        APPROVAL_REQUIRED
    }
    
    public enum MatchingStrategy {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY,
        BATCH_MATCH,
        SEQUENTIAL_MATCH,
        WEIGHTED_MATCH,
        ML_ASSISTED
    }
    
    public enum MatchingField {
        TRANSACTION_ID,
        EXTERNAL_REFERENCE,
        AMOUNT,
        DESCRIPTION,
        VALUE_DATE,
        PROCESSING_DATE,
        ACCOUNT_NUMBER,
        COUNTERPARTY,
        CURRENCY,
        BALANCE_BEFORE,
        BALANCE_AFTER,
        CUSTOM_FIELD_1,
        CUSTOM_FIELD_2,
        CUSTOM_FIELD_3
    }
    
    public boolean isApplicableToAccount(String accountNumber) {
        if (accountPattern == null || accountPattern.trim().isEmpty()) {
            return true; // Apply to all accounts if no pattern specified
        }
        
        return accountNumber.matches(accountPattern);
    }
    
    public boolean isApplicableToCurrency(String currency) {
        if (this.currency == null || this.currency.trim().isEmpty()) {
            return true; // Apply to all currencies if not specified
        }
        
        return this.currency.equalsIgnoreCase(currency);
    }
    
    public boolean isWithinTolerance(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) {
            return false;
        }
        
        BigDecimal difference = amount1.subtract(amount2).abs();
        
        // Check amount tolerance
        if (toleranceAmount != null && difference.compareTo(toleranceAmount) <= 0) {
            return true;
        }
        
        // Check percentage tolerance
        if (tolerancePercentage != null) {
            BigDecimal percentageDifference = difference
                .divide(amount1.abs(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            return percentageDifference.compareTo(tolerancePercentage) <= 0;
        }
        
        return difference.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public boolean isWithinDateVariance(java.time.LocalDateTime date1, java.time.LocalDateTime date2) {
        if (date1 == null || date2 == null || maxDateVarianceDays == null) {
            return true; // No date variance check
        }
        
        long daysDifference = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(date1, date2));
        return daysDifference <= maxDateVarianceDays;
    }
    
    public void incrementExecutionCount() {
        this.executionCount = (this.executionCount != null ? this.executionCount : 0L) + 1;
        this.lastExecutedAt = java.time.LocalDateTime.now();
    }
    
    public void incrementSuccessfulMatches() {
        this.successfulMatches = (this.successfulMatches != null ? this.successfulMatches : 0L) + 1;
    }
    
    public void incrementFailedMatches() {
        this.failedMatches = (this.failedMatches != null ? this.failedMatches : 0L) + 1;
    }
    
    public BigDecimal getSuccessRate() {
        if (executionCount == null || executionCount == 0) {
            return BigDecimal.ZERO;
        }

        long successful = successfulMatches != null ? successfulMatches : 0L;
        return BigDecimal.valueOf(successful)
            .divide(BigDecimal.valueOf(executionCount), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
}