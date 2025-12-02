package com.waqiti.gamification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "leaderboards", indexes = {
    @Index(name = "idx_leaderboard_type", columnList = "leaderboard_type"),
    @Index(name = "idx_leaderboard_period", columnList = "period_type"),
    @Index(name = "idx_leaderboard_user_rank", columnList = "user_id, rank_position"),
    @Index(name = "idx_leaderboard_score", columnList = "score"),
    @Index(name = "idx_leaderboard_period_start", columnList = "period_start")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Leaderboard {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "username", length = 100)
    private String username;
    
    @Column(name = "display_name", length = 150)
    private String displayName;
    
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    
    @Column(name = "leaderboard_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private LeaderboardType leaderboardType;
    
    @Column(name = "category", length = 50)
    private String category;
    
    @Column(name = "period_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PeriodType periodType;
    
    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;
    
    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;
    
    @Column(name = "score", nullable = false)
    private Long score;
    
    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;
    
    @Column(name = "previous_rank")
    private Integer previousRank;
    
    @Column(name = "rank_change")
    private Integer rankChange;
    
    @Column(name = "current_level")
    @Enumerated(EnumType.STRING)
    private UserPoints.Level currentLevel;
    
    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private Long totalPoints = 0L;
    
    @Column(name = "period_points", nullable = false)
    @Builder.Default
    private Long periodPoints = 0L;
    
    @Column(name = "transactions_count")
    @Builder.Default
    private Integer transactionsCount = 0;
    
    @Column(name = "challenges_completed")
    @Builder.Default
    private Integer challengesCompleted = 0;
    
    @Column(name = "badges_earned")
    @Builder.Default
    private Integer badgesEarned = 0;
    
    @Column(name = "referrals_count")
    @Builder.Default
    private Integer referralsCount = 0;
    
    @Column(name = "streak_days")
    @Builder.Default
    private Integer streakDays = 0;
    
    @Column(name = "is_eligible_for_rewards", nullable = false)
    @Builder.Default
    private Boolean isEligibleForRewards = true;
    
    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private Boolean isHidden = false;
    
    @Column(name = "region", length = 50)
    private String region;
    
    @Column(name = "country_code", length = 3)
    private String countryCode;
    
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;
    
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    public enum LeaderboardType {
        GLOBAL,
        FRIENDS,
        REGIONAL,
        CATEGORY,
        CHALLENGE,
        LEVEL_BASED,
        AGE_GROUP,
        COMPANY,
        CUSTOM
    }
    
    public enum PeriodType {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY,
        ALL_TIME,
        CUSTOM
    }
    
    public void updateRank(Integer newRank) {
        this.previousRank = this.rankPosition;
        this.rankPosition = newRank;
        this.rankChange = (previousRank != null) ? (previousRank - newRank) : 0;
    }
    
    public boolean hasImprovedRank() {
        return rankChange != null && rankChange > 0;
    }
    
    public boolean hasDroppedRank() {
        return rankChange != null && rankChange < 0;
    }
    
    public boolean isTopRank() {
        return rankPosition != null && rankPosition <= 3;
    }
    
    public boolean isTop10() {
        return rankPosition != null && rankPosition <= 10;
    }
    
    public boolean isTop100() {
        return rankPosition != null && rankPosition <= 100;
    }
}