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
import java.util.Set;
import java.util.HashSet;

/**
 * Family Account Domain Entity
 * 
 * Represents a family account structure with hierarchical relationships
 * and comprehensive parental control features.
 */
@Entity
@Table(name = "family_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FamilyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "family_id", unique = true, nullable = false)
    @NotBlank(message = "Family ID is required")
    @Size(max = 50)
    private String familyId;

    @Column(name = "family_name", nullable = false)
    @NotBlank(message = "Family name is required")
    @Size(max = 100)
    private String familyName;

    @Column(name = "primary_parent_user_id", nullable = false)
    @NotBlank(message = "Primary parent user ID is required")
    @Size(max = 50)
    private String primaryParentUserId;

    @Column(name = "secondary_parent_user_id")
    @Size(max = 50)
    private String secondaryParentUserId;

    @Column(name = "family_wallet_id")
    @Size(max = 50)
    private String familyWalletId;

    @Column(name = "total_family_balance", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Family balance cannot be negative")
    @Builder.Default
    private BigDecimal totalFamilyBalance = BigDecimal.ZERO;

    @Column(name = "monthly_family_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Monthly limit cannot be negative")
    private BigDecimal monthlyFamilyLimit;

    @Column(name = "current_month_spent", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Current month spent cannot be negative")
    @Builder.Default
    private BigDecimal currentMonthSpent = BigDecimal.ZERO;

    @Column(name = "default_daily_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Default daily limit cannot be negative")
    private BigDecimal defaultDailyLimit;

    @Column(name = "default_weekly_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Default weekly limit cannot be negative")
    private BigDecimal defaultWeeklyLimit;

    @Column(name = "default_monthly_limit", precision = 19, scale = 2)
    @DecimalMin(value = "0.00", message = "Default monthly limit cannot be negative")
    private BigDecimal defaultMonthlyLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "family_status", nullable = false)
    @Builder.Default
    private FamilyStatus familyStatus = FamilyStatus.ACTIVE;

    @Column(name = "parental_controls_enabled", nullable = false)
    @Builder.Default
    private Boolean parentalControlsEnabled = true;

    @Column(name = "educational_mode_enabled", nullable = false)
    @Builder.Default
    private Boolean educationalModeEnabled = true;

    @Column(name = "family_sharing_enabled", nullable = false)
    @Builder.Default
    private Boolean familySharingEnabled = true;

    @Column(name = "emergency_contact_phone")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String emergencyContactPhone;

    @Column(name = "family_pin_hash")
    private String familyPinHash;

    @Column(name = "family_timezone")
    @Size(max = 50)
    @Builder.Default
    private String familyTimezone = "UTC";

    @Column(name = "allowance_day_of_month")
    @Min(value = 1, message = "Allowance day must be between 1 and 28")
    @Max(value = 28, message = "Allowance day must be between 1 and 28")
    private Integer allowanceDayOfMonth;

    @Column(name = "auto_savings_enabled", nullable = false)
    @Builder.Default
    private Boolean autoSavingsEnabled = false;

    @Column(name = "auto_savings_percentage", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    @Builder.Default
    private BigDecimal autoSavingsPercentage = BigDecimal.ZERO;

    @OneToMany(mappedBy = "familyAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<FamilyMember> familyMembers = new HashSet<>();

    @OneToMany(mappedBy = "familyAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<FamilySpendingRule> spendingRules = new HashSet<>();

    @OneToMany(mappedBy = "familyAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<FamilyGoal> familyGoals = new HashSet<>();

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum FamilyStatus {
        ACTIVE,
        SUSPENDED,
        INACTIVE,
        CLOSED
    }

    // Business methods
    public boolean hasSecondaryParent() {
        return secondaryParentUserId != null && !secondaryParentUserId.trim().isEmpty();
    }

    public boolean isParent(String userId) {
        return primaryParentUserId.equals(userId) || 
               (hasSecondaryParent() && secondaryParentUserId.equals(userId));
    }

    public boolean canSpend(BigDecimal amount) {
        if (monthlyFamilyLimit == null) {
            return true;
        }
        return currentMonthSpent.add(amount).compareTo(monthlyFamilyLimit) <= 0;
    }

    public BigDecimal getRemainingMonthlyLimit() {
        if (monthlyFamilyLimit == null) {
            return BigDecimal.valueOf(Double.MAX_VALUE);
        }
        return monthlyFamilyLimit.subtract(currentMonthSpent).max(BigDecimal.ZERO);
    }

    public void addToMonthlySpent(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.currentMonthSpent = this.currentMonthSpent.add(amount);
        }
    }

    public void resetMonthlySpent() {
        this.currentMonthSpent = BigDecimal.ZERO;
    }

    public boolean isFamilyMember(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        return familyMembers.stream()
            .anyMatch(member -> userId.equals(member.getUserId()));
    }

    public int getFamilySize() {
        return familyMembers != null ? familyMembers.size() : 0;
    }

    public int getChildrenCount() {
        if (familyMembers == null) {
            return 0;
        }
        return (int) familyMembers.stream()
            .filter(member -> member.getMemberRole() == FamilyMember.MemberRole.CHILD ||
                            member.getMemberRole() == FamilyMember.MemberRole.TEEN)
            .count();
    }
}