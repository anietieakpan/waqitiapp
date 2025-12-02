/**
 * User Rewards Account Entity
 * Represents a user's rewards account with points and cashback balances
 */
package com.waqiti.rewards.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_rewards_account")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRewardsAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    // Points Balance
    @Column(name = "points_balance", nullable = false)
    private Long pointsBalance;
    
    @Column(name = "lifetime_points_earned", nullable = false)
    private Long lifetimePointsEarned;
    
    @Column(name = "lifetime_points_redeemed", nullable = false)
    private Long lifetimePointsRedeemed;
    
    @Column(name = "pending_points", nullable = false)
    private Long pendingPoints;
    
    // Tier Information
    @Column(name = "tier_id", nullable = false)
    private UUID tierId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", insertable = false, updatable = false)
    private RewardsTier currentTier;
    
    @Column(name = "tier_progress_points", nullable = false)
    private Long tierProgressPoints;
    
    @Column(name = "tier_expiry_date")
    private LocalDateTime tierExpiryDate;
    
    // Cashback
    @Column(name = "cashback_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashbackBalance;
    
    @Column(name = "lifetime_cashback_earned", nullable = false, precision = 15, scale = 2)
    private BigDecimal lifetimeCashbackEarned;
    
    @Column(name = "lifetime_cashback_redeemed", nullable = false, precision = 15, scale = 2)
    private BigDecimal lifetimeCashbackRedeemed;
    
    // Statistics
    @Column(name = "total_transactions", nullable = false)
    private Integer totalTransactions;
    
    @Column(name = "total_referrals", nullable = false)
    private Integer totalReferrals;
    
    @Column(name = "streak_days", nullable = false)
    private Integer streakDays;
    
    @Column(name = "last_activity_date")
    private LocalDateTime lastActivityDate;
    
    // Account Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;
    
    @Column(name = "enrollment_date", nullable = false)
    private LocalDateTime enrollmentDate;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (enrollmentDate == null) {
            enrollmentDate = LocalDateTime.now();
        }
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
        if (pointsBalance == null) {
            pointsBalance = 0L;
        }
        if (lifetimePointsEarned == null) {
            lifetimePointsEarned = 0L;
        }
        if (lifetimePointsRedeemed == null) {
            lifetimePointsRedeemed = 0L;
        }
        if (pendingPoints == null) {
            pendingPoints = 0L;
        }
        if (tierProgressPoints == null) {
            tierProgressPoints = 0L;
        }
        if (cashbackBalance == null) {
            cashbackBalance = BigDecimal.ZERO;
        }
        if (lifetimeCashbackEarned == null) {
            lifetimeCashbackEarned = BigDecimal.ZERO;
        }
        if (lifetimeCashbackRedeemed == null) {
            lifetimeCashbackRedeemed = BigDecimal.ZERO;
        }
        if (totalTransactions == null) {
            totalTransactions = 0;
        }
        if (totalReferrals == null) {
            totalReferrals = 0;
        }
        if (streakDays == null) {
            streakDays = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void addPoints(Long points) {
        this.pointsBalance += points;
        this.lifetimePointsEarned += points;
        this.tierProgressPoints += points;
    }
    
    public void deductPoints(Long points) {
        if (this.pointsBalance < points) {
            throw new IllegalArgumentException("Insufficient points balance");
        }
        this.pointsBalance -= points;
        this.lifetimePointsRedeemed += points;
    }
    
    public void addCashback(BigDecimal amount) {
        this.cashbackBalance = this.cashbackBalance.add(amount);
        this.lifetimeCashbackEarned = this.lifetimeCashbackEarned.add(amount);
    }
    
    public void deductCashback(BigDecimal amount) {
        if (this.cashbackBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient cashback balance");
        }
        this.cashbackBalance = this.cashbackBalance.subtract(amount);
        this.lifetimeCashbackRedeemed = this.lifetimeCashbackRedeemed.add(amount);
    }
    
    public void incrementTransactions() {
        this.totalTransactions++;
    }
    
    public void incrementReferrals() {
        this.totalReferrals++;
    }
    
    public void updateStreak(boolean continued) {
        if (continued) {
            this.streakDays++;
        } else {
            this.streakDays = 1;
        }
    }
    
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
    
    public enum AccountStatus {
        ACTIVE,
        SUSPENDED,
        CLOSED,
        FROZEN
    }
}