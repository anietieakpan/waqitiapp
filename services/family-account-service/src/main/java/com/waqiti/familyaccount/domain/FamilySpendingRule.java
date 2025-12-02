package com.waqiti.familyaccount.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.HashSet;

/**
 * Family Spending Rule Entity
 * 
 * Defines spending rules and restrictions that can be applied
 * to family members or the entire family account.
 */
@Entity
@Table(name = "family_spending_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FamilySpendingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_account_id", nullable = false)
    private FamilyAccount familyAccount;

    @Column(name = "rule_name", nullable = false)
    @NotBlank(message = "Rule name is required")
    @Size(max = 100)
    private String ruleName;

    @Column(name = "rule_description")
    @Size(max = 500)
    private String ruleDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_scope", nullable = false)
    private RuleScope ruleScope;

    @Column(name = "target_member_id")
    @Size(max = 50)
    private String targetMemberId;

    @Column(name = "target_age_group")
    @Enumerated(EnumType.STRING)
    private AgeGroup targetAgeGroup;

    // Amount-based rules
    @Column(name = "max_transaction_amount", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal maxTransactionAmount;

    @Column(name = "daily_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal dailyLimit;

    @Column(name = "weekly_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal weeklyLimit;

    @Column(name = "monthly_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal monthlyLimit;

    // Time-based rules
    @Column(name = "allowed_start_time")
    private LocalTime allowedStartTime;

    @Column(name = "allowed_end_time")
    private LocalTime allowedEndTime;

    @Column(name = "weekdays_only", nullable = false)
    private Boolean weekdaysOnly = false;

    @Column(name = "weekends_allowed", nullable = false)
    private Boolean weekendsAllowed = true;

    // Category-based rules
    @ElementCollection
    @CollectionTable(name = "rule_allowed_categories", 
                    joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "category")
    private Set<String> allowedCategories = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "rule_blocked_categories", 
                    joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "category")
    private Set<String> blockedCategories = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "rule_allowed_merchants", 
                    joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "merchant")
    private Set<String> allowedMerchants = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "rule_blocked_merchants", 
                    joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "merchant")
    private Set<String> blockedMerchants = new HashSet<>();

    // Transaction type rules
    @Column(name = "online_purchases_allowed", nullable = false)
    private Boolean onlinePurchasesAllowed = true;

    @Column(name = "atm_withdrawals_allowed", nullable = false)
    private Boolean atmWithdrawalsAllowed = true;

    @Column(name = "international_allowed", nullable = false)
    private Boolean internationalAllowed = true;

    @Column(name = "peer_payments_allowed", nullable = false)
    private Boolean peerPaymentsAllowed = true;

    @Column(name = "subscription_payments_allowed", nullable = false)
    private Boolean subscriptionPaymentsAllowed = true;

    // Approval requirements
    @Column(name = "requires_parent_approval", nullable = false)
    private Boolean requiresParentApproval = false;

    @Column(name = "approval_threshold", precision = 19, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal approvalThreshold;

    @Column(name = "automatic_decline", nullable = false)
    private Boolean automaticDecline = false;

    // Educational features
    @Column(name = "requires_quiz_completion", nullable = false)
    private Boolean requiresQuizCompletion = false;

    @Column(name = "educational_prompt")
    @Size(max = 500)
    private String educationalPrompt;

    @Column(name = "savings_goal_percentage", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal savingsGoalPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_status", nullable = false)
    private RuleStatus ruleStatus = RuleStatus.ACTIVE;

    @Column(name = "priority", nullable = false)
    @Min(value = 1)
    @Max(value = 10)
    private Integer priority = 5;

    @Column(name = "effective_date")
    private LocalDateTime effectiveDate;

    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    @Size(max = 50)
    private String createdBy;

    @Column(name = "updated_by")
    @Size(max = 50)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (effectiveDate == null) {
            effectiveDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RuleType {
        SPENDING_LIMIT,
        TIME_RESTRICTION,
        CATEGORY_RESTRICTION,
        MERCHANT_RESTRICTION,
        TRANSACTION_TYPE,
        APPROVAL_REQUIREMENT,
        EDUCATIONAL_REQUIREMENT,
        SAVINGS_ENFORCEMENT
    }

    public enum RuleScope {
        FAMILY_WIDE,
        INDIVIDUAL_MEMBER,
        AGE_GROUP,
        ROLE_BASED
    }

    public enum AgeGroup {
        CHILD_UNDER_13,
        TEEN_13_17,
        YOUNG_ADULT_18_25,
        ALL_CHILDREN
    }

    public enum RuleStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        EXPIRED
    }

    // Business methods
    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();
        
        if (ruleStatus != RuleStatus.ACTIVE) {
            return false;
        }
        
        if (effectiveDate != null && now.isBefore(effectiveDate)) {
            return false;
        }
        
        if (expirationDate != null && now.isAfter(expirationDate)) {
            return false;
        }
        
        return true;
    }

    public boolean appliesToMember(FamilyMember member) {
        if (!isEffective()) {
            return false;
        }

        switch (ruleScope) {
            case FAMILY_WIDE:
                return true;
            case INDIVIDUAL_MEMBER:
                return member.getUserId().equals(targetMemberId);
            case AGE_GROUP:
                return matchesAgeGroup(member);
            case ROLE_BASED:
                return matchesRole(member);
            default:
                return false;
        }
    }

    private boolean matchesAgeGroup(FamilyMember member) {
        if (targetAgeGroup == null) {
            return false;
        }

        BigDecimal age = member.calculateAge();
        
        switch (targetAgeGroup) {
            case CHILD_UNDER_13:
                return age.compareTo(BigDecimal.valueOf(13)) < 0;
            case TEEN_13_17:
                return age.compareTo(BigDecimal.valueOf(13)) >= 0 && 
                       age.compareTo(BigDecimal.valueOf(18)) < 0;
            case YOUNG_ADULT_18_25:
                return age.compareTo(BigDecimal.valueOf(18)) >= 0 && 
                       age.compareTo(BigDecimal.valueOf(26)) < 0;
            case ALL_CHILDREN:
                return age.compareTo(BigDecimal.valueOf(18)) < 0;
            default:
                return false;
        }
    }

    private boolean matchesRole(FamilyMember member) {
        // Role-based rules could be extended based on member roles
        return member.isChild();
    }

    public boolean allowsTransaction(BigDecimal amount, String category, String merchant, String transactionType) {
        if (!isEffective()) {
            return true; // If rule is not effective, don't restrict
        }

        // Check amount limits
        if (maxTransactionAmount != null && amount.compareTo(maxTransactionAmount) > 0) {
            return false;
        }

        // Check category restrictions
        if (!blockedCategories.isEmpty() && blockedCategories.contains(category)) {
            return false;
        }

        if (!allowedCategories.isEmpty() && !allowedCategories.contains(category)) {
            return false;
        }

        // Check merchant restrictions
        if (!blockedMerchants.isEmpty() && blockedMerchants.contains(merchant)) {
            return false;
        }

        if (!allowedMerchants.isEmpty() && !allowedMerchants.contains(merchant)) {
            return false;
        }

        // Check transaction type restrictions
        switch (transactionType.toUpperCase()) {
            case "ONLINE":
                return onlinePurchasesAllowed;
            case "ATM":
                return atmWithdrawalsAllowed;
            case "INTERNATIONAL":
                return internationalAllowed;
            case "PEER":
                return peerPaymentsAllowed;
            case "SUBSCRIPTION":
                return subscriptionPaymentsAllowed;
            default:
                return true;
        }
    }

    public boolean isTimeAllowed() {
        LocalDateTime now = LocalDateTime.now();
        
        // Check weekday/weekend restrictions
        boolean isWeekend = now.getDayOfWeek().getValue() >= 6;
        
        if (weekdaysOnly && isWeekend) {
            return false;
        }
        
        if (!weekendsAllowed && isWeekend) {
            return false;
        }

        // Check time window
        if (allowedStartTime != null && allowedEndTime != null) {
            LocalTime currentTime = now.toLocalTime();
            return !currentTime.isBefore(allowedStartTime) && 
                   !currentTime.isAfter(allowedEndTime);
        }

        return true;
    }
}