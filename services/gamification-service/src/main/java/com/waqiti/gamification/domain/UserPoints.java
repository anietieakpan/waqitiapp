package com.waqiti.gamification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_points", indexes = {
    @Index(name = "idx_user_points_user_id", columnList = "user_id"),
    @Index(name = "idx_user_points_level", columnList = "current_level"),
    @Index(name = "idx_user_points_total", columnList = "total_points")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPoints {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;
    
    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private Long totalPoints = 0L;
    
    @Column(name = "available_points", nullable = false)
    @Builder.Default
    private Long availablePoints = 0L;
    
    @Column(name = "redeemed_points", nullable = false)
    @Builder.Default
    private Long redeemedPoints = 0L;
    
    @Column(name = "current_level", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Level currentLevel = Level.BRONZE;
    
    @Column(name = "level_progress_points", nullable = false)
    @Builder.Default
    private Long levelProgressPoints = 0L;
    
    @Column(name = "next_level_threshold")
    private Long nextLevelThreshold;
    
    @Column(name = "cashback_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal cashbackRate = new BigDecimal("0.50");
    
    @Column(name = "multiplier_active")
    @Builder.Default
    private Boolean multiplierActive = false;
    
    @Column(name = "current_multiplier", precision = 3, scale = 1)
    @Builder.Default
    private BigDecimal currentMultiplier = BigDecimal.ONE;
    
    @Column(name = "multiplier_expires_at")
    private LocalDateTime multiplierExpiresAt;
    
    @Column(name = "streak_days", nullable = false)
    @Builder.Default
    private Integer streakDays = 0;
    
    @Column(name = "last_activity_date")
    private LocalDateTime lastActivityDate;
    
    @Column(name = "monthly_points", nullable = false)
    @Builder.Default
    private Long monthlyPoints = 0L;
    
    @Column(name = "weekly_points", nullable = false)
    @Builder.Default
    private Long weeklyPoints = 0L;
    
    @Column(name = "daily_points", nullable = false)
    @Builder.Default
    private Long dailyPoints = 0L;
    
    @OneToMany(mappedBy = "userPoints", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PointTransaction> pointTransactions = new HashSet<>();
    
    @OneToMany(mappedBy = "userPoints", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserBadge> badges = new HashSet<>();
    
    @OneToMany(mappedBy = "userPoints", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserChallenge> challenges = new HashSet<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    public enum Level {
        BRONZE(0L, 999L, new BigDecimal("0.5")),
        SILVER(1000L, 4999L, new BigDecimal("1.0")),
        GOLD(5000L, 19999L, new BigDecimal("1.5")),
        PLATINUM(20000L, 49999L, new BigDecimal("2.0")),
        DIAMOND(50000L, Long.MAX_VALUE, new BigDecimal("3.0"));
        
        private final Long minPoints;
        private final Long maxPoints;
        private final BigDecimal cashbackRate;
        
        Level(Long minPoints, Long maxPoints, BigDecimal cashbackRate) {
            this.minPoints = minPoints;
            this.maxPoints = maxPoints;
            this.cashbackRate = cashbackRate;
        }
        
        public Long getMinPoints() { return minPoints; }
        public Long getMaxPoints() { return maxPoints; }
        public BigDecimal getCashbackRate() { return cashbackRate; }
        
        public static Level fromPoints(Long points) {
            for (Level level : values()) {
                if (points >= level.minPoints && points <= level.maxPoints) {
                    return level;
                }
            }
            return BRONZE;
        }
    }
}