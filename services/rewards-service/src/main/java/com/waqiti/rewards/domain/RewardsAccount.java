package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.AccountStatus;
import com.waqiti.rewards.enums.LoyaltyTier;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rewards_accounts",
    indexes = {
        @Index(name = "idx_rewards_account_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_rewards_account_status", columnList = "status"),
        @Index(name = "idx_rewards_account_tier", columnList = "current_tier")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RewardsAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;
    
    @Column(name = "cashback_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal cashbackBalance = BigDecimal.ZERO;
    
    @Column(name = "points_balance", nullable = false)
    @Builder.Default
    private Long pointsBalance = 0L;
    
    @Column(name = "lifetime_cashback", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal lifetimeCashback = BigDecimal.ZERO;
    
    @Column(name = "lifetime_points", nullable = false)
    @Builder.Default
    private Long lifetimePoints = 0L;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "current_tier", nullable = false)
    @Builder.Default
    private LoyaltyTier currentTier = LoyaltyTier.BRONZE;
    
    @Column(name = "tier_progress", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal tierProgress = BigDecimal.ZERO;
    
    @Column(name = "tier_progress_target", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal tierProgressTarget = new BigDecimal("1000.00");
    
    @Column(name = "tier_upgrade_date")
    private Instant tierUpgradeDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;
    
    @Column(name = "enrollment_date", nullable = false)
    private Instant enrollmentDate;
    
    @Column(name = "last_activity")
    private Instant lastActivity;
    
    @Embedded
    private UserRewardsPreferences preferences;
    
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CashbackTransaction> cashbackTransactions = new ArrayList<>();
    
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PointsTransaction> pointsTransactions = new ArrayList<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    // Business methods
    public void addCashback(BigDecimal amount) {
        this.cashbackBalance = this.cashbackBalance.add(amount);
        this.lifetimeCashback = this.lifetimeCashback.add(amount);
        this.lastActivity = Instant.now();
    }
    
    public void addPoints(Long points) {
        this.pointsBalance = this.pointsBalance + points;
        this.lifetimePoints = this.lifetimePoints + points;
        this.lastActivity = Instant.now();
    }
    
    public boolean canRedeemCashback(BigDecimal amount) {
        return this.status == AccountStatus.ACTIVE && 
               this.cashbackBalance.compareTo(amount) >= 0;
    }
    
    public boolean canRedeemPoints(Long points) {
        return this.status == AccountStatus.ACTIVE && 
               this.pointsBalance >= points;
    }
    
    public void deductCashback(BigDecimal amount) {
        if (!canRedeemCashback(amount)) {
            throw new IllegalStateException("Insufficient cashback balance");
        }
        this.cashbackBalance = this.cashbackBalance.subtract(amount);
        this.lastActivity = Instant.now();
    }
    
    public void deductPoints(Long points) {
        if (!canRedeemPoints(points)) {
            throw new IllegalStateException("Insufficient points balance");
        }
        this.pointsBalance = this.pointsBalance - points;
        this.lastActivity = Instant.now();
    }
}